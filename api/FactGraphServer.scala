package gov.irs.factgraph.api

import cats.effect.*
import org.http4s.*
import org.http4s.dsl. io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe. parser.*
import io.circe.syntax.*
import com.comcast.ip4s.*

import gov.irs.factgraph.*
import gov.irs.factgraph.persisters. InMemoryPersister
import gov.irs.factgraph. types.*

import scala.io.Source
import java.io. File

// Request/Response case classes
case class SetFactRequest(path: String, value: String)
case class GetFactRequest(path: String)
case class BatchSetRequest(facts: List[SetFactRequest])
case class LoadDictionaryRequest(xml: String)
case class LoadGraphRequest(json: String)
case class FactResponse(path: String, value: Option[String], success: Boolean, error: Option[String] = None)
case class GraphResponse(json: String)
case class PathsResponse(paths: List[String])
case class HealthResponse(status: String, version: String)

object FactGraphServer extends IOApp: 
  
  // Thread-safe state management
  private var currentDictionary: Option[FactDictionary] = None
  private var currentGraph: Option[Graph] = None
  
  // Load default dictionary from file if exists
  private def loadDefaultDictionary(): Unit =
    val defaultPath = sys.env.getOrElse("FACT_DICTIONARY_PATH", "/app/dictionaries/default.xml")
    val file = new File(defaultPath)
    if file.exists() then
      val xml = Source.fromFile(file).mkString
      currentDictionary = Some(FactDictionary.importFromXml(xml))
      currentGraph = currentDictionary.map(d => Graph(d))
      println(s"Loaded default dictionary from $defaultPath")
  
  // Routes
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    
    // Health check
    case GET -> Root / "health" =>
      Ok(HealthResponse("healthy", "3.1.0-SNAPSHOT").asJson)
    
    // Load a fact dictionary from XML
    case req @ POST -> Root / "dictionary" / "load" =>
      req.as[LoadDictionaryRequest]. flatMap { body =>
        try
          val dictionary = FactDictionary.importFromXml(body.xml)
          currentDictionary = Some(dictionary)
          currentGraph = Some(Graph(dictionary))
          Ok(Json.obj("success" -> true. asJson, "message" -> "Dictionary loaded successfully". asJson))
        catch
          case e: Exception =>
            BadRequest(Json. obj("success" -> false.asJson, "error" -> e.getMessage.asJson))
      }
    
    // Get all available paths in the dictionary
    case GET -> Root / "paths" =>
      currentGraph match
        case Some(graph) =>
          val paths = graph.dictionary.paths.map(_. toString).toList
          Ok(PathsResponse(paths).asJson)
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))
    
    // Set a single fact
    case req @ POST -> Root / "fact" / "set" =>
      req.as[SetFactRequest]. flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              graph.set(Path(body.path), body.value)
              graph.save()
              Ok(FactResponse(body.path, Some(body.value), success = true).asJson)
            catch
              case e: Exception =>
                BadRequest(FactResponse(body.path, None, success = false, error = Some(e.getMessage)).asJson)
          case None =>
            BadRequest(Json.obj("error" -> "No graph initialized".asJson))
      }
    
    // Set multiple facts in batch
    case req @ POST -> Root / "facts" / "set" =>
      req.as[BatchSetRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            val results = body.facts.map { fact =>
              try
                graph. set(Path(fact.path), fact.value)
                FactResponse(fact.path, Some(fact.value), success = true)
              catch
                case e: Exception =>
                  FactResponse(fact.path, None, success = false, error = Some(e.getMessage))
            }
            graph.save()
            Ok(results.asJson)
          case None =>
            BadRequest(Json. obj("error" -> "No graph initialized".asJson))
      }
    
    // Get a fact value
    case req @ POST -> Root / "fact" / "get" =>
      req.as[GetFactRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              val result = graph.get(Path(body.path))
              val value = result match
                case Some(v) => Some(v.toString)
                case None => None
              Ok(FactResponse(body.path, value, success = true).asJson)
            catch
              case e: Exception =>
                BadRequest(FactResponse(body.path, None, success = false, error = Some(e.getMessage)).asJson)
          case None =>
            BadRequest(Json. obj("error" -> "No graph initialized".asJson))
      }
    
    // Get the entire graph as JSON
    case GET -> Root / "graph" =>
      currentGraph match
        case Some(graph) =>
          val json = graph.persister.asInstanceOf[InMemoryPersister].toJson(2)
          Ok(GraphResponse(json).asJson)
        case None =>
          BadRequest(Json.obj("error" -> "No graph initialized".asJson))
    
    // Load graph state from JSON
    case req @ POST -> Root / "graph" / "load" =>
      req.as[LoadGraphRequest].flatMap { body =>
        currentDictionary match
          case Some(dictionary) =>
            try
              val persister = InMemoryPersister(body.json)
              currentGraph = Some(Graph(dictionary, persister))
              Ok(Json.obj("success" -> true.asJson))
            catch
              case e:  Exception =>
                BadRequest(Json.obj("success" -> false.asJson, "error" -> e.getMessage.asJson))
          case None =>
            BadRequest(Json.obj("error" -> "No dictionary loaded". asJson))
      }
    
    // Reset the graph (keep dictionary, clear facts)
    case POST -> Root / "graph" / "reset" =>
      currentDictionary match
        case Some(dictionary) =>
          currentGraph = Some(Graph(dictionary))
          Ok(Json.obj("success" -> true. asJson))
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))
  }
  
  override def run(args: List[String]): IO[ExitCode] =
    // Load default dictionary on startup
    loadDefaultDictionary()
    
    val port = sys.env.getOrElse("PORT", "8080").toInt
    
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(Port. fromInt(port).get)
      .withHttpApp(routes.orNotFound)
      .build
      . use(_ => IO.never)
      .as(ExitCode.Success)
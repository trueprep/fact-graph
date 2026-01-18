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

import gov.irs.factgraph.{FactDictionary, Graph, Path}
import gov.irs.factgraph.persisters.InMemoryPersister
import gov.irs.factgraph.types.Dollar
import gov.irs.factgraph.types.WritableType

import scala.io.Source
import java.io.File

// Request/Response case classes
case class SetFactRequest(path: String, value: Json)
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
  
  // Load dictionary from file or directory
  private def loadDefaultDictionary(): Unit =
    val defaultPath = sys.env.getOrElse("FACT_DICTIONARY_PATH", "/app/dictionaries")
    val file = new File(defaultPath)
    
    if file.isDirectory then
      // Load all XML files from directory
      val xmlFiles = file.listFiles().filter(_.getName.endsWith(".xml"))
      
      if xmlFiles.nonEmpty then
        println(s"Loading ${xmlFiles.length} dictionary files from $defaultPath")
        
        // Try loading each file individually first
        try
          val dictionaries = xmlFiles.map { f =>
            val xml = Source.fromFile(f).mkString
            FactDictionary.importFromXml(xml)
          }
          
          // Merge dictionaries (use first one as base)
          currentDictionary = Some(dictionaries.head)
          currentGraph = currentDictionary.map(d => Graph(d))
          println(s"Successfully loaded ${xmlFiles.length} dictionary files individually")
          xmlFiles.foreach(f => println(s"  - ${f.getName}"))
        catch
          case e: Exception =>
            println(s"Error loading dictionaries individually: ${e.getMessage}")
            println("Falling back to first file only...")
            // Fallback: try loading just the first file
            val xml = Source.fromFile(xmlFiles.head).mkString
            currentDictionary = Some(FactDictionary.importFromXml(xml))
            currentGraph = currentDictionary.map(d => Graph(d))
            println(s"Loaded single dictionary: ${xmlFiles.head.getName}")
      else
        println(s"No XML files found in $defaultPath")
    else if file.exists() then
      // Single file
      val xml = Source.fromFile(file).mkString
      currentDictionary = Some(FactDictionary.importFromXml(xml))
      currentGraph = currentDictionary.map(d => Graph(d))
      println(s"Loaded dictionary from $defaultPath")
  
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
          val paths = graph.dictionary.getPaths().map(_.toString).toList
          Ok(PathsResponse(paths).asJson)
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))
    
    // Set a single fact
    case req @ POST -> Root / "fact" / "set" =>
      req.as[SetFactRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              val maybeDefinition = graph.dictionary.getDefinition(body.path)
              if (maybeDefinition == null) then
                BadRequest(
                  FactResponse(
                    body.path,
                    None,
                    success = false,
                    error = Some(s"Unknown fact path: ${body.path}"),
                  ).asJson,
                )
              else
                val typeNode = maybeDefinition.typeNode
                val coerced: Either[String, WritableType] =
                  typeNode match
                    case "DollarNode" =>
                      body.value.asNumber.flatMap(_.toBigDecimal) match
                        case Some(n) => Right(Dollar(n))
                        case None =>
                          body.value.asString match
                            case Some(s) =>
                              try Right(Dollar(s))
                              catch case e: Exception => Left(e.getMessage)
                            case None =>
                              Left("Expected a number or numeric string")
                    case "IntNode" =>
                      body.value.asNumber.flatMap(_.toInt) match
                        case Some(i) => Right(i)
                        case None =>
                          body.value.asString match
                            case Some(s) =>
                              try Right(s.toInt)
                              catch case e: Exception => Left(e.getMessage)
                            case None =>
                              Left("Expected an integer")
                    case "BooleanNode" =>
                      body.value.asBoolean match
                        case Some(b) => Right(b)
                        case None =>
                          body.value.asString match
                            case Some(s) =>
                              s.trim.toLowerCase match
                                case "true"  => Right(true)
                                case "false" => Right(false)
                                case _       => Left("Expected 'true' or 'false'")
                            case None =>
                              Left("Expected a boolean")
                    case "StringNode" =>
                      body.value.asString match
                        case Some(s) => Right(s)
                        case None    => Left("Expected a string")
                    case other =>
                      Left(s"Unsupported writable type: $other")

                coerced match
                  case Left(err) =>
                    BadRequest(
                      FactResponse(body.path, None, success = false, error = Some(err)).asJson,
                    )
                  case Right(writable) =>
                    val (ok, violations) = graph.set(Path(body.path), writable)
                    if ok then Ok(FactResponse(body.path, Some(writable.toString), success = true).asJson)
                    else
                      BadRequest(
                        FactResponse(
                          body.path,
                          None,
                          success = false,
                          error = Some(
                            s"Limit violation(s): ${violations.map(_.toString).mkString("; ")}",
                          ),
                        ).asJson,
                      )
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
                val maybeDefinition = graph.dictionary.getDefinition(fact.path)
                if (maybeDefinition == null) then
                  FactResponse(
                    fact.path,
                    None,
                    success = false,
                    error = Some(s"Unknown fact path: ${fact.path}"),
                  )
                else
                  val typeNode = maybeDefinition.typeNode
                  val coerced: Either[String, WritableType] =
                    typeNode match
                      case "DollarNode" =>
                        fact.value.asNumber.flatMap(_.toBigDecimal) match
                          case Some(n) => Right(Dollar(n))
                          case None =>
                            fact.value.asString match
                              case Some(s) =>
                                try Right(Dollar(s))
                                catch case e: Exception => Left(e.getMessage)
                              case None =>
                                Left("Expected a number or numeric string")
                      case "IntNode" =>
                        fact.value.asNumber.flatMap(_.toInt) match
                          case Some(i) => Right(i)
                          case None =>
                            fact.value.asString match
                              case Some(s) =>
                                try Right(s.toInt)
                                catch case e: Exception => Left(e.getMessage)
                              case None =>
                                Left("Expected an integer")
                      case "BooleanNode" =>
                        fact.value.asBoolean match
                          case Some(b) => Right(b)
                          case None =>
                            fact.value.asString match
                              case Some(s) =>
                                s.trim.toLowerCase match
                                  case "true"  => Right(true)
                                  case "false" => Right(false)
                                  case _       => Left("Expected 'true' or 'false'")
                              case None =>
                                Left("Expected a boolean")
                      case "StringNode" =>
                        fact.value.asString match
                          case Some(s) => Right(s)
                          case None    => Left("Expected a string")
                      case other =>
                        Left(s"Unsupported writable type: $other")

                  coerced match
                    case Left(err) =>
                      FactResponse(fact.path, None, success = false, error = Some(err))
                    case Right(writable) =>
                      val (ok, violations) = graph.set(Path(fact.path), writable)
                      if ok then FactResponse(fact.path, Some(writable.toString), success = true)
                      else
                        FactResponse(
                          fact.path,
                          None,
                          success = false,
                          error = Some(
                          s"Limit violation(s): ${violations.map(_.toString).mkString("; ")}",
                          ),
                        )
              catch
                case e: Exception =>
                  FactResponse(fact.path, None, success = false, error = Some(e.getMessage))
            }
            Ok(results.asJson)
          case None =>
            BadRequest(Json.obj("error" -> "No graph initialized".asJson))
      }
    
    // Get a fact value
    case req @ POST -> Root / "fact" / "get" =>
      req.as[GetFactRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              val result = graph.get(Path(body.path))
              // Result monad has .value method that returns Option[A]
              val value = result.value.map(_.toString)
              Ok(FactResponse(body.path, value, success = true).asJson)
            catch
              case e: Exception =>
                BadRequest(FactResponse(body.path, None, success = false, error = Some(e.getMessage)).asJson)
          case None =>
            BadRequest(Json.obj("error" -> "No graph initialized".asJson))
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
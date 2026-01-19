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
import gov.irs.factgraph.types.{Dollar, Day, Tin, Ein, IpPin, PhoneNumber, EmailAddress, Address, BankAccount, WritableType}

import scala.io.Source
import java.io.File
import scala.xml.{Elem, Node, NodeSeq, XML}
import upickle.default.read

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
case class FactDefinitionResponse(path: String, typeNode: String, isWritable: Boolean, rawXml: String)
case class RawXmlResponse(path: String, xml: String)
case class DependencyResponse(path: String, dependencies: List[DependencyInfo])
case class ReverseDependencyResponse(path: String, reverseDependencies: List[String])
case class DependencyInfo(path: String, module: Option[String])
case class ExplainResponse(path: String, currentValue: Option[String], isComplete: Boolean, dependencies: List[ExplainNode], rawXml: Option[String])
case class ExplainNode(path: String, currentValue: Option[String], isComplete: Boolean, rawXml: Option[String])
case class GraphSnapshotResponse(snapshot: String, timestamp: Long, factCount: Int)
case class GraphDiffRequest(beforeSnapshot: String, afterSnapshot: String)
case class GraphDiffResponse(changedPaths: List[String], addedPaths: List[String], removedPaths: List[String])
case class AddToCollectionRequest(path: String, uuid: String)
case class RemoveFromCollectionRequest(path: String, uuid: String)

object FactGraphServer extends IOApp:

  // Thread-safe state management
  private var currentDictionary: Option[FactDictionary] = None
  private var currentGraph: Option[Graph] = None

  // Dependency indexes built at startup
  private var forwardDeps: Map[String, List[DependencyInfo]] = Map.empty
  private var reverseDeps: Map[String, Set[String]] = Map.empty

  // Load dictionary from file or directory
  private def loadDefaultDictionary(): Unit =
    val defaultPath = sys.env.getOrElse("FACT_DICTIONARY_PATH", "/app/dictionaries")
    val file = new File(defaultPath)

    if file.isDirectory then
      val xmlFiles = file.listFiles().filter(_.getName.endsWith(".xml")).sortBy(_.getName)

      if xmlFiles.nonEmpty then
        println(s"Loading ${xmlFiles.length} dictionary files from $defaultPath")

        try
          val allFacts: Seq[Node] =
            xmlFiles.flatMap { f =>
              val moduleXml = XML.loadFile(f)
              // Each file is expected to be a FactDictionaryModule; we extract all Fact nodes and merge them.
              (moduleXml \\ "Fact")
            }.toSeq

          val combinedModule: Elem =
            <FactDictionaryModule>
              <Facts>{allFacts}</Facts>
            </FactDictionaryModule>

          val dictionary = FactDictionary.fromXml(NodeSeq.fromSeq(Seq(combinedModule)))
          currentDictionary = Some(dictionary)
          currentGraph = Some(Graph(dictionary))

          // Build dependency indexes
          buildDependencyIndexes(dictionary)

          println(s"Successfully loaded merged dictionary from ${xmlFiles.length} files")
          xmlFiles.foreach(f => println(s"  - ${f.getName}"))
        catch
          case e: Exception =>
            println(s"Error loading merged dictionaries from directory '$defaultPath': ${e.getMessage}")
            throw e
      else
        println(s"No XML files found in $defaultPath")
    else if file.exists() then
      // Single file
      val xml = Source.fromFile(file).mkString
      val dictionary = FactDictionary.importFromXml(xml)
      currentDictionary = Some(dictionary)
      currentGraph = Some(Graph(dictionary))

      // Build dependency indexes
      buildDependencyIndexes(dictionary)

      println(s"Loaded dictionary from $defaultPath")

  // Build forward and reverse dependency indexes from dictionary XML
  private def buildDependencyIndexes(dictionary: FactDictionary): Unit =
    val forwardDepsBuilder = scala.collection.mutable.Map[String, List[DependencyInfo]]()
    val reverseDepsBuilder = scala.collection.mutable.Map[String, scala.collection.mutable.Set[String]]()

    // Parse each fact's XML for dependencies
    dictionary.getDefinitionsAsNodes().foreach { case (path, xmlNode) =>
      val factPath = path.toString
      val dependencies = scala.collection.mutable.ListBuffer[DependencyInfo]()

      // Find all Dependency nodes in this fact's XML
      val depNodes = xmlNode \\ "Dependency"
      depNodes.foreach { depNode =>
        val depPath = (depNode \ "@path").text.trim
        val depModule = (depNode \ "@module").text.trim match
          case "" => None
          case m => Some(m)

        dependencies += DependencyInfo(depPath, depModule)

        // Add to reverse index
        val reverseSet = reverseDepsBuilder.getOrElseUpdate(depPath, scala.collection.mutable.Set[String]())
        reverseSet += factPath
      }

      if (dependencies.nonEmpty) {
        forwardDepsBuilder(factPath) = dependencies.toList
      }
    }

    // Convert mutable to immutable
    forwardDeps = forwardDepsBuilder.toMap
    reverseDeps = reverseDepsBuilder.map { case (k, v) => (k, v.toSet) }.toMap

    println(s"Built dependency indexes: ${forwardDeps.size} facts with dependencies, ${reverseDeps.size} dependency targets")

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

          // Build dependency indexes
          buildDependencyIndexes(dictionary)

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

    // Get fact definition metadata
    case GET -> Root / "fact" / "definition" :? pathParam =>
      currentGraph match
        case Some(graph) =>
          val pathStr = pathParam.getOrElse("path", Nil).headOption
          pathStr match
            case Some(p) =>
              val maybeDefinition = graph.dictionary.getDefinition(p)
              if (maybeDefinition != null) then
                // Determine if writable by checking if there's a derived node
                val rawXml = graph.dictionary.getDefinitionsAsNodes().get(Path(p))
                val isWritable = rawXml.exists(xml => (xml \\ "Writable").nonEmpty)
                Ok(FactDefinitionResponse(p, maybeDefinition.typeNode, isWritable, rawXml.map(_.toString).getOrElse("")).asJson)
              else
                NotFound(Json.obj("error" -> s"Fact not found: $p".asJson))
            case None =>
              BadRequest(Json.obj("error" -> "Missing 'path' query parameter".asJson))
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))

    // Get raw XML for a fact
    case GET -> Root / "fact" / "raw_xml" :? pathParam =>
      currentGraph match
        case Some(graph) =>
          val pathStr = pathParam.getOrElse("path", Nil).headOption
          pathStr match
            case Some(p) =>
              val rawXml = graph.dictionary.getDefinitionsAsNodes().get(Path(p))
              rawXml match
                case Some(xml) =>
                  Ok(RawXmlResponse(p, xml.toString).asJson)
                case None =>
                  NotFound(Json.obj("error" -> s"Raw XML not found for fact: $p".asJson))
            case None =>
              BadRequest(Json.obj("error" -> "Missing 'path' query parameter".asJson))
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))

    // Get forward dependencies for a fact
    case GET -> Root / "fact" / "deps" :? pathParam =>
      currentGraph match
        case Some(graph) =>
          val pathStr = pathParam.getOrElse("path", Nil).headOption
          pathStr match
            case Some(p) =>
              val deps = forwardDeps.getOrElse(p, List.empty)
              Ok(DependencyResponse(p, deps).asJson)
            case None =>
              BadRequest(Json.obj("error" -> "Missing 'path' query parameter".asJson))
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))

    // Get reverse dependencies for a fact
    case GET -> Root / "fact" / "reverse_deps" :? pathParam =>
      currentGraph match
        case Some(graph) =>
          val pathStr = pathParam.getOrElse("path", Nil).headOption
          pathStr match
            case Some(p) =>
              val reverseDepsList = reverseDeps.getOrElse(p, Set.empty).toList.sorted
              Ok(ReverseDependencyResponse(p, reverseDepsList).asJson)
            case None =>
              BadRequest(Json.obj("error" -> "Missing 'path' query parameter".asJson))
        case None =>
          BadRequest(Json.obj("error" -> "No dictionary loaded".asJson))

    // Explain a fact with dependency tree and current values
    case GET -> Root / "fact" / "explain" :? pathParam :? includeXmlParam =>
      currentGraph match
        case Some(graph) =>
          val pathStr = pathParam.getOrElse("path", Nil).headOption
          val includeXml = includeXmlParam.getOrElse("includeXml", Nil).headOption.getOrElse("false").toBoolean
          pathStr match
            case Some(p) =>
              try
                // Get current value and completeness for root fact
                val currentResult = graph.get(Path(p))
                val currentValue = currentResult.value.map(_.toString)
                val isComplete = currentResult.complete

                // Get dependencies and their current state
                val deps = forwardDeps.getOrElse(p, List.empty)
                val explainNodes = deps.flatMap { dep =>
                  try
                    val depResult = graph.get(Path(dep.path))
                    val depValue = depResult.value.map(_.toString)
                    val depComplete = depResult.complete
                    val depXml = if (includeXml) {
                      graph.dictionary.getDefinitionsAsNodes().get(Path(dep.path)).map(_.toString)
                    } else None

                    Some(ExplainNode(dep.path, depValue, depComplete, depXml))
                  catch
                    case _: Exception => None // Skip dependencies that can't be evaluated
                }

                // Get raw XML for root fact if requested
                val rootXml = if (includeXml) {
                  graph.dictionary.getDefinitionsAsNodes().get(Path(p)).map(_.toString)
                } else None

                Ok(ExplainResponse(p, currentValue, isComplete, explainNodes, rootXml).asJson)
              catch
                case e: Exception =>
                  BadRequest(Json.obj("error" -> s"Failed to explain fact '$p': ${e.getMessage}".asJson))
            case None =>
              BadRequest(Json.obj("error" -> "Missing 'path' query parameter".asJson))
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
                    case "DayNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(Day(s))
                          catch case e: Exception => Left(s"Invalid date format: ${e.getMessage}")
                        case None =>
                          body.value.asNumber.flatMap(_.toLong) match
                            case Some(epochMs) =>
                              try Right(Day(java.time.LocalDate.ofEpochDay(epochMs / (24 * 60 * 60 * 1000))))
                              catch case e: Exception => Left(s"Invalid epoch timestamp: ${e.getMessage}")
                            case None => Left("Expected a date string (ISO-8601) or epoch timestamp")
                    case "TinNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(Tin(s))
                          catch case e: Exception => Left(s"Invalid TIN format: ${e.getMessage}")
                        case None => Left("Expected a TIN string")
                    case "EinNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(Ein(s))
                          catch case e: Exception => Left(s"Invalid EIN format: ${e.getMessage}")
                        case None => Left("Expected an EIN string")
                    case "IpPinNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(IpPin(s))
                          catch case e: Exception => Left(s"Invalid IP PIN format: ${e.getMessage}")
                        case None => Left("Expected an IP PIN string")
                    case "PhoneNumberNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(PhoneNumber(s))
                          catch case e: Exception => Left(s"Invalid phone number format: ${e.getMessage}")
                        case None => Left("Expected a phone number string")
                    case "EmailAddressNode" =>
                      body.value.asString match
                        case Some(s) =>
                          try Right(EmailAddress(s))
                          catch case e: Exception => Left(s"Invalid email format: ${e.getMessage}")
                        case None =>                       Left("Expected an email string")
                    case "AddressNode" =>
                      body.value.asObject match
                        case Some(obj) =>
                          val streetAddress = obj("streetAddress").flatMap(_.asString).getOrElse("")
                          val city = obj("city").flatMap(_.asString).getOrElse("")
                          val postalCode = obj("postalCode").flatMap(_.asString).getOrElse("")
                          val stateOrProvence = obj("stateOrProvence").flatMap(_.asString).getOrElse("")
                          val streetAddressLine2 = obj("streetAddressLine2").flatMap(_.asString).getOrElse("")
                          val country = obj("country").flatMap(_.asString).getOrElse("United States of America")
                          try Right(Address(streetAddress, city, postalCode, stateOrProvence, streetAddressLine2, country))
                          catch case e: Exception => Left(s"Invalid address format: ${e.getMessage}")
                        case None =>
                          Left("Expected an address object with fields: streetAddress, city, postalCode, stateOrProvence, [streetAddressLine2], [country]")
                    case "BankAccountNode" =>
                      body.value.asObject match
                        case Some(obj) =>
                          val accountType = obj("accountType").flatMap(_.asString).getOrElse("")
                          val routingNumber = obj("routingNumber").flatMap(_.asString).getOrElse("")
                          val accountNumber = obj("accountNumber").flatMap(_.asString).getOrElse("")
                          try Right(BankAccount(accountType, routingNumber, accountNumber))
                          catch case e: Exception => Left(s"Invalid bank account format: ${e.getMessage}")
                        case None =>
                          Left("Expected a bank account object with fields: accountType, routingNumber, accountNumber")
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
                      case "DayNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(Day(s))
                            catch case e: Exception => Left(s"Invalid date format: ${e.getMessage}")
                          case None =>
                            fact.value.asNumber.flatMap(_.toLong) match
                              case Some(epochMs) =>
                                try Right(Day(java.time.LocalDate.ofEpochDay(epochMs / (24 * 60 * 60 * 1000))))
                                catch case e: Exception => Left(s"Invalid epoch timestamp: ${e.getMessage}")
                              case None => Left("Expected a date string (ISO-8601) or epoch timestamp")
                      case "TinNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(Tin(s))
                            catch case e: Exception => Left(s"Invalid TIN format: ${e.getMessage}")
                          case None => Left("Expected a TIN string")
                      case "EinNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(Ein(s))
                            catch case e: Exception => Left(s"Invalid EIN format: ${e.getMessage}")
                          case None => Left("Expected an EIN string")
                      case "IpPinNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(IpPin(s))
                            catch case e: Exception => Left(s"Invalid IP PIN format: ${e.getMessage}")
                          case None => Left("Expected an IP PIN string")
                      case "PhoneNumberNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(PhoneNumber(s))
                            catch case e: Exception => Left(s"Invalid phone number format: ${e.getMessage}")
                          case None => Left("Expected a phone number string")
                      case "EmailAddressNode" =>
                        fact.value.asString match
                          case Some(s) =>
                            try Right(EmailAddress(s))
                            catch case e: Exception => Left(s"Invalid email format: ${e.getMessage}")
                          case None =>                       Left("Expected an email string")
                      case "AddressNode" =>
                        fact.value.asObject match
                          case Some(obj) =>
                            val streetAddress = obj("streetAddress").flatMap(_.asString).getOrElse("")
                            val city = obj("city").flatMap(_.asString).getOrElse("")
                            val postalCode = obj("postalCode").flatMap(_.asString).getOrElse("")
                            val stateOrProvence = obj("stateOrProvence").flatMap(_.asString).getOrElse("")
                            val streetAddressLine2 = obj("streetAddressLine2").flatMap(_.asString).getOrElse("")
                            val country = obj("country").flatMap(_.asString).getOrElse("United States of America")
                            try Right(Address(streetAddress, city, postalCode, stateOrProvence, streetAddressLine2, country))
                            catch case e: Exception => Left(s"Invalid address format: ${e.getMessage}")
                          case None =>
                            Left("Expected an address object with fields: streetAddress, city, postalCode, stateOrProvence, [streetAddressLine2], [country]")
                      case "BankAccountNode" =>
                        fact.value.asObject match
                          case Some(obj) =>
                            val accountType = obj("accountType").flatMap(_.asString).getOrElse("")
                            val routingNumber = obj("routingNumber").flatMap(_.asString).getOrElse("")
                            val accountNumber = obj("accountNumber").flatMap(_.asString).getOrElse("")
                            try Right(BankAccount(accountType, routingNumber, accountNumber))
                            catch case e: Exception => Left(s"Invalid bank account format: ${e.getMessage}")
                          case None =>
                            Left("Expected a bank account object with fields: accountType, routingNumber, accountNumber")
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

    // Get a snapshot of current graph state with metadata
    case POST -> Root / "graph" / "snapshot" =>
      currentGraph match
        case Some(graph) =>
          val json = graph.persister.asInstanceOf[InMemoryPersister].toJson(2)
          val factCount = graph.dictionary.getPaths().size
          val timestamp = System.currentTimeMillis()
          Ok(GraphSnapshotResponse(json, timestamp, factCount).asJson)
        case None =>
          BadRequest(Json.obj("error" -> "No graph initialized".asJson))

    // Compare two snapshots and return differences
    case req @ POST -> Root / "graph" / "diff" =>
      req.as[GraphDiffRequest].flatMap { body =>
        try
          // Parse both snapshots as JSON objects
          val beforeJson = parse(body.beforeSnapshot).getOrElse(Json.obj())
          val afterJson = parse(body.afterSnapshot).getOrElse(Json.obj())

          // Extract fact maps, filtering out metadata fields
          val beforeFacts = beforeJson.asObject.map(_.toMap).getOrElse(Map.empty[String, Json])
            .filterNot { case (k, _) => k == "migrations" }
          val afterFacts = afterJson.asObject.map(_.toMap).getOrElse(Map.empty[String, Json])
            .filterNot { case (k, _) => k == "migrations" }

          // Get all paths
          val beforePaths = beforeFacts.keySet
          val afterPaths = afterFacts.keySet

          // Calculate differences
          val addedPaths = (afterPaths -- beforePaths).toList.sorted
          val removedPaths = (beforePaths -- afterPaths).toList.sorted
          val potentiallyChangedPaths = (beforePaths intersect afterPaths).toList

          // Check which overlapping paths actually changed
          val changedPaths = potentiallyChangedPaths.filter { path =>
            val beforeValue = beforeFacts.get(path)
            val afterValue = afterFacts.get(path)
            beforeValue != afterValue
          }.sorted

          Ok(GraphDiffResponse(changedPaths, addedPaths, removedPaths).asJson)
        catch
          case e: Exception =>
            BadRequest(Json.obj("error" -> s"Failed to diff snapshots: ${e.getMessage}".asJson))
      }

    // Add item to collection
    case req @ POST -> Root / "collection" / "add" =>
      req.as[AddToCollectionRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              val maybeDefinition = graph.dictionary.getDefinition(body.path)
              if (maybeDefinition == null) then
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Unknown path: ${body.path}".asJson
                ))
              else if (maybeDefinition.typeNode != "CollectionNode") then
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Path '${body.path}' is not a collection".asJson
                ))
              else
                graph.addToCollection(body.path, body.uuid)
                val fullPath = s"${body.path}/#${body.uuid}"
                Ok(Json.obj(
                  "path" -> fullPath.asJson,
                  "success" -> true.asJson
                ))
            catch
              case e: IllegalArgumentException =>
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Invalid UUID format: ${e.getMessage}".asJson
                ))
              case e: Exception =>
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> e.getMessage.asJson
                ))
          case None =>
            BadRequest(Json.obj("error" -> "No graph initialized".asJson))
      }

    // Remove item from collection
    case req @ POST -> Root / "collection" / "remove" =>
      req.as[RemoveFromCollectionRequest].flatMap { body =>
        currentGraph match
          case Some(graph) =>
            try
              val maybeDefinition = graph.dictionary.getDefinition(body.path)
              if (maybeDefinition == null) then
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Unknown path: ${body.path}".asJson
                ))
              else if (maybeDefinition.typeNode != "CollectionNode") then
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Path '${body.path}' is not a collection".asJson
                ))
              else
                graph.removeFromCollection(body.path, body.uuid)
                Ok(Json.obj("success" -> true.asJson))
            catch
              case e: IllegalArgumentException =>
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> s"Invalid UUID format: ${e.getMessage}".asJson
                ))
              case e: Exception =>
                BadRequest(Json.obj(
                  "success" -> false.asJson,
                  "error" -> e.getMessage.asJson
                ))
          case None =>
            BadRequest(Json.obj("error" -> "No graph initialized".asJson))
      }
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
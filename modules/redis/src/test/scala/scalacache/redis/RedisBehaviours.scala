package scalacache.redis
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Inside, Matchers}
import redis.clients.jedis.{BinaryJedisCommands, Jedis, JedisCommands}
import redis.clients.util.Pool
import scalacache.CacheAlg
import scalacache.serialization.Codec.DecodingResult
import scalacache.serialization.binary._
import scalacache.serialization.{Codec, FailedToDecode}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait RedisBehaviours extends RedisSerialization with ScalaFutures with Matchers with Inside with Eventually {
  this: FlatSpec =>
  type JClient <: JedisCommands with BinaryJedisCommands
  type JPool <: Pool[JClient]

  import scalacache.modes.scalaFuture._

  import scala.concurrent.ExecutionContext.Implicits.global

  case object AlwaysFailing
  implicit val alwaysFailingCodec: Codec[AlwaysFailing.type] = new Codec[AlwaysFailing.type] {
    override def encode(value: AlwaysFailing.type): Array[Byte] = Array(0)
    override def decode(bytes: Array[Byte]): DecodingResult[AlwaysFailing.type] =
      Left(FailedToDecode(new Exception("Failed to decode")))
  }

  def constructCache[V](pool: JPool)(implicit codec: Codec[V]): CacheAlg[V]

  def redisCache[P <: Pool[Jedis]](pool: => JPool): Unit = {

    behavior of "get"

    it should "return the value stored in Redis" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      client.set(bytes("key1"), serialize(123))
      whenReady(cache.get("key1")) { _ should be(Some(123)) }
    }

    it should "return None if the given key does not exist in the underlying cache" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      whenReady(cache.get("non-existent-key")) { _ should be(None) }
    }

    it should "raise an error if decoding fails" in {
      val client = pool.getResource
      val failingCache = constructCache[AlwaysFailing.type](pool)

      client.set(bytes("key1"), serialize(123))
      whenReady(failingCache.get("key1").failed) { t =>
        inside(t) { case FailedToDecode(e) => e.getMessage should be("Failed to decode") }
      }
    }

    behavior of "put"

    it should "store the given key-value pair in the underlying cache" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      whenReady(cache.put("key2")(123, None)) { _ =>
        deserialize[Int](client.get(bytes("key2"))) should be(Right(123))
      }
    }

    behavior of "put with TTL"

    it should "store the given key-value pair in the underlying cache" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      whenReady(cache.put("key3")(123, Some(1 second))) { _ =>
        deserialize[Int](client.get(bytes("key3"))) should be(Right(123))

        // Should expire after 1 second
        eventually(timeout(Span(2, Seconds))) {
          client.get(bytes("key3")) should be(null)
        }
      }
    }

    behavior of "put with TTL of zero"

    it should "store the given key-value pair in the underlying cache with no expiry" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      whenReady(cache.put("key4")(123, Some(Duration.Zero))) { _ =>
        deserialize[Int](client.get(bytes("key4"))) should be(Right(123))
        client.ttl(bytes("key4")) should be(-1L)
      }
    }

    behavior of "put with TTL of less than 1 second"

    it should "store the given key-value pair in the underlying cache" in {
      val client = pool.getResource
      val cache = constructCache[Int](pool)

      whenReady(cache.put("key5")(123, Some(100 milliseconds))) { _ =>
        deserialize[Int](client.get(bytes("key5"))) should be(Right(123))
        client.pttl("key5").toLong should be > 0L

        // Should expire after 1 second
        eventually(timeout(Span(2, Seconds))) {
          client.get(bytes("key5")) should be(null)
        }
      }
    }

    behavior of "caching with serialization"

    def roundTrip[V](key: String, value: V)(implicit codec: Codec[V]): Future[Option[V]] = {
      val c = constructCache[V](pool)
      c.put(key)(value, None).flatMap(_ => c.get(key))
    }

    it should "round-trip a String" in {
      whenReady(roundTrip("string", "hello")) { _ should be(Some("hello")) }
    }

    it should "round-trip a byte array" in {
      whenReady(roundTrip("bytearray", bytes("world"))) { result =>
        new String(result.get, "UTF-8") should be("world")
      }
    }

    it should "round-trip an Int" in {
      whenReady(roundTrip("int", 345)) { _ should be(Some(345)) }
    }

    it should "round-trip a Double" in {
      whenReady(roundTrip("double", 1.23)) { _ should be(Some(1.23)) }
    }

    it should "round-trip a Long" in {
      whenReady(roundTrip("long", 3456L)) { _ should be(Some(3456L)) }
    }

    it should "round-trip a Serializable case class" in {
      val cc = CaseClass(123, "wow")
      whenReady(roundTrip("caseclass", cc)) { _ should be(Some(cc)) }
    }

    behavior of "remove"

    it should "delete the given key and its value from the underlying cache" in {

      val client = pool.getResource
      val cache = constructCache[Int](pool)

      client.set(bytes("key1"), serialize(123))
      deserialize[Int](client.get(bytes("key1"))) should be(Right(123))

      whenReady(cache.remove("key1")) { _ =>
        client.get("key1") should be(null)
      }
    }

  }

  private def bytes(s: String) = s.getBytes("utf-8")

}

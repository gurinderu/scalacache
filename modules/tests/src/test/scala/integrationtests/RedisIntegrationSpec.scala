package integrationtests

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.scalatest.{FlatSpec, Matchers}
import redis.clients.jedis.JedisPool
import scalacache.redis.RedisCache

class RedisIntegrationSpec extends FlatSpec with Matchers with ForAllTestContainer with CacheBehaviours {
  private final val redisPort = 6379
  override val container = GenericContainer("redis:alpine", Seq(redisPort))
  private var pool: JedisPool = _

  override def afterStart(): Unit = {
    pool = new JedisPool(container.containerIpAddress, container.mappedPort(redisPort))
  }

  override def beforeStop(): Unit = {
    pool.close()
  }

  it should behave like cacheWithDifferentEffects("(Redis) ⇔ (binary codec)", {
    import scalacache.serialization.binary._
    RedisCache[String](pool)
  })

  it should behave like cacheWithDifferentEffects("(Redis) ⇔ (circe codec)", {
    import scalacache.serialization.circe._
    RedisCache[String](pool)
  })

}

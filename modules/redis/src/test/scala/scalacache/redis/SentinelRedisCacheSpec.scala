package scalacache.redis

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, LazyContainer, MultipleContainers}
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.scalatest.FlatSpec
import redis.clients.jedis._
import scalacache._
import scalacache.serialization.Codec

import scala.collection.JavaConverters._

class SentinelRedisCacheSpec extends FlatSpec with ForAllTestContainer with RedisBehaviours {

  type JClient = Jedis
  type JPool = JedisSentinelPool
  private final val sentinelPort = 26379
  private final val redisPort = 6379
  private val container1 =
    GenericContainer("bitnami/redis:latest", Seq(redisPort), env = Map("ALLOW_EMPTY_PASSWORD" -> "true"))
  private val container2 = LazyContainer(
    GenericContainer(
      "bitnami/redis-sentinel:latest",
      Seq(sentinelPort),
      Map("REDIS_MASTER_HOST" -> container1.containerIpAddress,
          "REDIS_MASTER_PORT_NUMBER" -> container1.mappedPort(redisPort).toString)
    ))
  override val container = MultipleContainers(container1, container2)

  private var pool: JedisSentinelPool = _

  override def afterStart(): Unit = {
    pool = new JedisSentinelPool(
      "mymaster",
      Set(s"${container2.container.containerIpAddress}:${container2.container.mappedPort(sentinelPort)}").asJava,
      new GenericObjectPoolConfig
    )

    println()
  }

  override def beforeStop(): Unit = {
    pool.close()
  }

  def constructCache[V](pool: JPool)(implicit codec: Codec[V]): CacheAlg[V] =
    new SentinelRedisCache[V](pool)

  it should behave like redisCache(pool)

}

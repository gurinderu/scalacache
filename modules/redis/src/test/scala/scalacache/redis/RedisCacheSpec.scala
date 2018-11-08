package scalacache.redis

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.scalatest.FlatSpec
import redis.clients.jedis._
import scalacache._
import scalacache.serialization.Codec

import scala.language.postfixOps

class RedisCacheSpec extends FlatSpec with ForAllTestContainer with RedisBehaviours {

  type JClient = Jedis
  type JPool = JedisPool
  private final val redisPort = 6379
  override val container = GenericContainer("redis:alpine", Seq(redisPort))
  private var pool: JedisPool = _

  override def afterStart(): Unit = {
    pool = new JedisPool(container.containerIpAddress, container.mappedPort(redisPort))
  }

  override def beforeStop(): Unit = {
    pool.close()
  }

  def constructCache[V](pool: JPool)(implicit codec: Codec[V]): CacheAlg[V] =
    new RedisCache[V](jedisPool = pool)

  it should behave like redisCache(pool)

}

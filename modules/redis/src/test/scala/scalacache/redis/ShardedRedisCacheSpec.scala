package scalacache.redis

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, GenericContainer, MultipleContainers}
import org.scalatest.FlatSpec
import redis.clients.jedis.{JedisPoolConfig, JedisShardInfo, ShardedJedis, ShardedJedisPool}
import scalacache._
import scalacache.serialization.Codec

class ShardedRedisCacheSpec extends FlatSpec with ForAllTestContainer with RedisBehaviours {
  type JClient = ShardedJedis
  type JPool = ShardedJedisPool

  private final val redisPort = 6379
  private val container1 = GenericContainer("redis:alpine", Seq(redisPort))
  private val container2 = GenericContainer("redis:alpine", Seq(redisPort))
  override val container: Container = MultipleContainers(container1, container2)
  private var pool: ShardedJedisPool = _

  override def afterStart(): Unit = {
    val shard1 = new JedisShardInfo(container1.containerIpAddress, container1.mappedPort(redisPort))
    val shard2 = new JedisShardInfo(container2.containerIpAddress, container2.mappedPort(redisPort))

    pool = new ShardedJedisPool(new JedisPoolConfig(), java.util.Arrays.asList(shard1, shard2))
  }

  override def beforeStop(): Unit = {
    pool.close()
  }

  def constructCache[V](pool: JPool)(implicit codec: Codec[V]): CacheAlg[V] =
    new ShardedRedisCache[V](jedisPool = pool)

  it should behave like redisCache(pool)

}

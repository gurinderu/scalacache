package scalacache.redis

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import redis.clients.jedis.JedisPool
import scalacache.memoization._
import scalacache.modes.sync._
import scalacache.serialization.binary._

case class User(id: Int, name: String)

/**
  * Test to check the sample code in issue #32 runs OK
  * (just to isolate the use of the List[User] type from the Play classloader problem)
  */
class Issue32Spec extends FlatSpec with Matchers with BeforeAndAfter with ForAllTestContainer {

  private final val redisPort = 6379
  override val container = GenericContainer("redis:alpine", Seq(redisPort))
  private var pool: JedisPool = _

  override def afterStart(): Unit = {
    pool = new JedisPool(container.containerIpAddress, container.mappedPort(redisPort))
  }

  override def beforeStop(): Unit = {
    pool.close()
  }

  "memoize and Redis" should "work with List[User]" in {
    implicit val cache: RedisCache[List[User]] = RedisCache[List[User]](pool)

    def getUser(id: Int): List[User] = memoizeSync(None) {
      List(User(id, "Taro"))
    }
    getUser(1) should be(List(User(1, "Taro")))
    getUser(1) should be(List(User(1, "Taro")))
  }

}

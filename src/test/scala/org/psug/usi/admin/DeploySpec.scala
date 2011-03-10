package org.psug.usi.domain

/**
 * User: alag
 * Date: 2/17/11
 * Time: 12:19 AM
 */

import org.specs._
import net.liftweb.json._
import net.liftweb.json.Serialization.{read, write}
import com.sun.jersey.api.client.Client

import org.psug.usi.system._

import org.psug.usi.Main
import actors.remote._
import org.psug.usi.store.DataPulled
import org.psug.usi.service.ServiceStatus

class DeploySpec extends SpecificationWithJUnit {

  implicit val formats = Serialization.formats(NoTypeHints)

  val webPort: Int = 34567
  val servicesPort: Int = 55554

  def webResource(path: String) = new Client().resource("http://localhost:" + webPort + path)

  val context = new SpecContext {
    val main = new Main
    after(main.stop)
  }

  "main launcher" should {

    "start as web server on given port given arguments 'web'" in {
      context.main.start("Web", "" + webPort)
      val result = webResource("/admin/status").header("Content-Type", "application/json").get(classOf[String])
      val status = read[Status](result)
      status.nodeType must be_==("Web")
      status.port must be_==(webPort)
    }

    "start as service on given port with arguments 'service'" in {
      context.main.start("Service","" + servicesPort)
      val node = Node("localhost", servicesPort)
      val actor = RemoteActor.select(node, 'UserRepositoryService)
      val status : Some[Status] = (actor !? (1000,ServiceStatus)).asInstanceOf[Some[Status]]
      status.get.nodeType must be_==("Service")
      status.get.port must be_==(servicesPort)
    }

  }
}


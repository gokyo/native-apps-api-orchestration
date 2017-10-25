package stubs

import stubs.StubShortcuts._

object PushRegistrationStub {

  def pushRegistrationSucceeds() : Unit = {
    stubPostSuccess("/push/registration", """{}""")
  }

}

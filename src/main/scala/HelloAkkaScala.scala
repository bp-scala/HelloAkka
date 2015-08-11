import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

case object Greet
case class WhoToGreet(who: String)
case class Greeting(message: String)

class Greeter extends Actor {
  var greeting = "akka"

  def receive = {
    case WhoToGreet(who) => greeting = s"hello, $who"
    case Greet           => {
      sender ! Greeting(greeting)
    } // Send the current greeting back to the sender
    case Kill => context stop self
  }

  override def postStop() = {
    println("We are stopping now!")
  }



}

object HelloAkkaScala extends App {

  // Create the 'helloakka' actor system
  val system = ActorSystem("helloakka")

  // Create the 'greeter' actor
  val greeter = system.actorOf(Props[Greeter], "greeter")

  // Create an "actor-in-a-box"
  val inbox = Inbox.create(system)

  // Tell the 'greeter' to change its 'greeting' message
  greeter.tell(WhoToGreet("akka"), ActorRef.noSender)

  // Ask the 'greeter for the latest 'greeting'
  // Reply should go to the "actor-in-a-box"
  inbox.send(greeter, Greet)

  // Wait 5 seconds for the reply with the 'greeting' message
  val Greeting(message1) = inbox.receive(5.seconds)
  println(s"Greeting: $message1")

  // Change the greeting and ask for it again
  greeter.tell(WhoToGreet("typesafe"), ActorRef.noSender)
  inbox.send(greeter, Greet)
  val Greeting(message2) = inbox.receive(5.seconds)
  println(s"Greeting: $message2")

  val greetPrinter = system.actorOf(Props(new GreetPrinter(system)))


  // after zero seconds, send a Greet message every second to the greeter with a sender of the greetPrinter

  system.scheduler.scheduleOnce(5.seconds, greetPrinter, Kill)(system.dispatcher)
  
}

case object Kill

// prints a greeting
class GreetPrinter(actorSystem: ActorSystem) extends Actor {
  val greeter2 = context.actorOf(Props[Greeter])
  //val scheduler = actorSystem.scheduler.schedule(0.seconds, 1.second, greeter2, Greet)(actorSystem.dispatcher, self)
  val scheduler = actorSystem.actorOf(Props(new Scheduler(actorSystem, 0.seconds, 1.second, greeter2, Some(context.self), Greet)))
  def receive = {
    case Greeting(message) => println("msg:"+message)
    case Kill => {
      scheduler ! Kill
    }
    case Killed => {
      greeter2 ! Kill
      context stop self
    }
  }
}

case object SchedulerMsg
case object Killed

class Scheduler(actorSystem: ActorSystem,
                initialDelay: FiniteDuration,
                interval: FiniteDuration,
                receiver: ActorRef,
                sender: Option[ActorRef],
                message: Any
               ) extends Actor {
  actorSystem.scheduler.scheduleOnce(initialDelay, context.self, SchedulerMsg)(actorSystem.dispatcher, self)
  var killer: Option[ActorRef] = None
  def receive = {

    case Kill => {
      killer = Some(context.sender())
    }

    case SchedulerMsg => if(killer.isEmpty){
      actorSystem.scheduler.scheduleOnce(interval, context.self, SchedulerMsg)(actorSystem.dispatcher, self)
      receiver.tell(message, sender.getOrElse(self))
    }else{
      killer.get ! Killed
      context stop self
    }
  }

}




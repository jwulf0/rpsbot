# RpsBot

A Telegram Bot for playing [Rock-Paper-Scissors](https://en.wikipedia.org/wiki/Rock–paper–scissors) written in Scala.
 
It uses [Canoe](https://github.com/augustjune/canoe) for the Telegram integration (not Canoe's Scenario DSL though). 

This is my first project trying the "purely functional" way of scala and using an abstract effect type described by cats typeclasses.

## Try it out

For testing, if you want to spin up a bot in another language or with a different wording, you can simply build new implementations for `rpsbot.bot.communication.operations.UserMessageParser` and `rpsbot.bot.communication.operations.FeedbackTranslator` and create your bot application as an fs2 Stream using `rpsbot.bot.RpsBotApp.create` as in `rpsbot.Boot` (or simply change the `lazy val interaction` in `Boot`). You can find information on how to create a Telegram Bot and obtain its token in the [Telegram Bot Api Documentation](https://core.telegram.org/bots#3-how-do-i-create-a-bot). You need a DynamoDB table (or local stack) or use the in memory storage implementation.

If you are also getting into this way of scala programming and are trying to understand the application, I recommend checking out `rpsbot.bot.communication.UserMessageHandler` which contains the core "business logic" of the application, and then retracing how it is used in `rpsbot.bot.RpsBottApp.create` which is called by `rpsbot.Boot`, the entrypoint to an example application.

If you are on the other hand very experienced with cats/fs2/purely functional programming and have some advice, please feel free to message me or open an issue. :)

## TODOs 

* The method signatures of the `Storage` trait do not reflect the fact that something could go wrong at runtime (like a non-reachable database in case of persistent storage) and, subsequently, the implementations ignore possible errors by dangerously calling `.get` and the likes, which might cause the application to crash at runtime.

* `Storage` is also tightly coupled to RPS because one of the parameters of `Storage.updateMatch` is of type `rpsbot.rps.model.Match`. This could be prevented by making `Storage` polymorphic in the Match type, i.e. adding a type parameter `M` to the trait (so that it's `Storage[F[_], M]`) and updating the method to `def updateMatch(lobbyId: Int, updatedMatch: M, timestamp: Int): F[Lobby]`. When going down that road, one might also decouple the `UserMessageHandler` from RPS completely, making it into something like a generic "Lobby- and Game-Move-Handler for 2 Player Games playable by sending texts to a Telegram Bot", which seems like a great example for overengineering but also like an interesting exercise so I might do that in the future.

## Further notes

The name, like the command to create/join lobbies as found in `rpsbot.MessageParserDE`, stems from "Schnick Schnack Schnuck", one of the more common names of the many names the game has in German. [[1]](https://de.wikipedia.org/wiki/Schere,_Stein,_Papier)   
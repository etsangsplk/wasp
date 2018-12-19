WASPClient = require(__dirname + "/wasp/client")

client = new WASPClient("localhost", 9000, "staging", "firestar")
client.add("test", "test", "newValue", (status) ->
  client.get("test", "test", (data) -> console.log data)
)

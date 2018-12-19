# WASP Client
JS Client for WASP services. Supported operations are
- ADD
- GET

## Getting Started
```
$ npm install
// Starts the coffee compiler in watch mode
$ npm run compile
// If you don't want to run it on watch mode,
$ npm run compileOnce

// In another terminal window
$ npm test
```

## Usages
### In Coffee
```
wasp = require('wasp')
WASPClient = wasp.client
P = wasp.P

client = new WASPClient("localhost", 9000, "staging", "firestar")
client.add("test", "test", "newValue", (status) ->
  client.get(P("test"), P("test"), (data) -> console.log data)
)
```

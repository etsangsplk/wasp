rfr = require 'rfr'
WASPClient = rfr 'lib/wasp/client'
P = rfr('lib/wasp/path').P
nock = require 'nock'

nock('http://host:9000').get(/\/configuration.*/).reply(200, {test: 'ok'})

describe 'WASPClient', ->
  @timeout(4000)
  it 'should test WASPClient'

  it 'should return the path as it is when its string', ->
    client = new WASPClient()
    client._parsePath("firestar").should.equal("firestar")

  it 'should return the toString of Path when its Path', ->
    client = new WASPClient()
    client._parsePath(P("firestar")).should.equal("firestar")

  it 'should return an event which fire waspDataUpdate', (done) ->
    client = new WASPClient("host",9000,"env","namespace")
    client.scheduledGet(P("www.tek-micro.com")).once('waspDataUpdate', (data)->
      done()
    )

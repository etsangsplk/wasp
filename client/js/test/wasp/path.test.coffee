rfr = require('rfr')
should = require('chai').should()
P = rfr('lib/wasp/path').P

describe 'Path', ->
  it 'should return the string as it is, if there are no . in the path', ->
    P("stringWithNoDots").should.deep.equal(P("stringWithNoDots"))

  it 'should return escaped string when path contains .', ->
    "firestar.contains".should.deep.equal(P("firestar").P("contains").path)
    P("firestar").P("www.host.com").path.should.deep.equal("firestar.www\\.host\\.com")

  it 'should return * for null string', ->
    P(null).path.should.deep.equal("*")

  it 'should return * for undefined string', ->
    P(undefined).path.should.deep.equal("*")

  it '[TODO] - should return an already escaped path as it is'
    # P("firestar\\.contains").should.equal("firestar\\.contains")

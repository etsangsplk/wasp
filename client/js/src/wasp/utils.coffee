Path = require('./path').Path
P = require('./path').P

class WASPUtils
  constructor: (@env, @component) ->
    @env ?= 'staging'

  parsePath: (path) ->
    if typeof path == 'object' and path instanceof Path
      path.toString()
    else if typeof path is 'string'
      @escapeUrl(path)
    else path

  escapeUrl: (url) ->
    P(url).toString()

module.exports = WASPUtils

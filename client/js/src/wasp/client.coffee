request = require 'request'
url = require 'url'
Path = require('./path').Path
Utils = require './utils'
EventEmitter = require 'events'

class WASPClient
  constructor: (@host, @port, @environment, @component) ->
    @environment ?= "staging" # Default to staging when no env is given
    @defaultHeaders =
      headers:
        "content-type":"text/plain;charset=UTF-8"
    @utils = new Utils @environment, @component
  get: (path, callback) ->
    path = @utils.parsePath(path)
    callback ?= () -> {}
    request(@_serviceUrl(path), @defaultHeaders, (error, response, body) ->
      # TODO: Handle response properly and return status and the reponse body instead of [null, boolean]. See L#60, L#65.
      if error? or response.statusCode != 200 then callback(null) else callback(JSON.parse(body))
    )

  getKeys: (path, callback) ->
    path = @_parsePath(path)
    callback ?= () -> {}
    request(@_serviceUrl(path, {}, 'keys'), @defaultHeaders, (error, response, body) ->
      if error? or response.statusCode != 200 then callback(null) else callback(JSON.parse(body))
    )

  getDefault: (path, callback) ->
    path = @_parsePath(path)
    callback ?= () -> {}
    request(@_serviceUrl(path, {}, 'default'), @defaultHeaders, (error, response, body) ->
      if error? or response.statusCode != 200 then callback(null) else callback(JSON.parse(body))
    )

  add: (path, value, authParams, callback) ->
    if not value? then throw new Error("You forgot to add a value for the path: " + path)
    path = @_parsePath(path)

    callback ?= () -> {}
    valueInJSON = JSON.stringify(value)
    request.post(url: @_serviceUrl(path, authParams), body: valueInJSON, headers: @defaultHeaders.headers, (error, response) ->
      if error? or response.statusCode != 200 then callback(false) else callback(true)
    )

  addLink: (path, sourcePath, authParams, callback) ->
    if not sourcePath? then throw new Error("You forgot to add sourcepath for the path: " + path)
    path = @_parsePath(path)

    callback ?= () -> {}

    request.post(url: @_serviceUrl(path, authParams, 'link'), body: "\"#{sourcePath}\"", headers: @defaultHeaders.headers, (error, response) ->
      callback(response.statusCode, response.body)
    )

  getReference: (path, callback = () -> {}) ->
    path = @_parsePath path

    request @_serviceUrl(path, {}, 'reference'), @defaultHeaders, (error, response) ->
      if error then callback response.statusCode, error else callback response.statusCode, response.body

  getDependencyList: (path, callback = () -> {}) ->

    request @_serviceUrl(path, {}, 'keys'), @defaultHeaders, (error, response) ->
      if error then callback response.statusCode, error else callback response.statusCode, response.body

  update: (path, value, authParams, callback) ->
    if not value? then throw new Error("You forgot to add a value for the key: " + path + "." + key)
    path = @_parsePath(path)

    callback ?= () -> {}
    valueInJSON = JSON.stringify(value)
    request.patch(url: @_serviceUrl(path, authParams), body: valueInJSON, headers: @defaultHeaders.headers, (error, response) ->
      if error? or response.statusCode != 200 then callback(false) else callback(true)
    )

  delete: (path, authParams, callback) ->
    path = @_parsePath(path)

    callback ?= () -> {}

    request.del(url: @_serviceUrl(path, authParams), headers: @defaultHeaders.headers, (error, response) ->
      if error? or response.statusCode != 200 then callback(false) else callback(true)
    )

  _serviceUrl: (path, authParams, method) ->
    path = [@environment, @component, @_parsePath(path)]
      .map((str) -> str.toString().trim())
      .filter((str) -> str? and str.length > 0)
      .join('.')
    endpoint = if typeof method == 'undefined' or method == null then '' else "/#{method}"
    queryObject = {}
    if authParams?
        queryObject = authParams
    queryObject.path = path
    url.format(protocol: "http", hostname: @host, port: @port, pathname: "/configuration#{endpoint}", query: queryObject)

  _parsePath: (path) -> if typeof path == 'object' && path instanceof Path then path.toString() else path

  scheduledGet: (path, interval=3000) =>
    event = new EventEmitter()
    setInterval(
      () =>
        @get(path, (data)->
          event.emit 'waspDataUpdate', data
        )
      , interval
    )
    event

module.exports = WASPClient

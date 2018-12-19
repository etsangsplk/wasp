class Path
  constructor: (existing, path) ->
    path ?= "*"
    escapedPath = path.replace(/\./g, '\\.')
    @path = if existing? then [existing, escapedPath].join('.') else escapedPath
  P: (newPath) -> new Path(@path, newPath)
  toString: () -> @path

P = (path) -> new Path(null, path)

exports.P = P
exports.Path = Path

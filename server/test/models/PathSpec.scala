package models

import org.scalatest.{FlatSpec, Matchers}

class PathSpec extends FlatSpec with Matchers {

  "Path" should "be split on '.'(dot)" in {
    Path.from("a.b.c") should be (Path(List(MatchNamespace("a"), MatchNamespace("b"), MatchNamespace("c"))))
    Path.from("a.b").append(MatchNamespace("c")) should be (Path(List(MatchNamespace("a"), MatchNamespace("b"), MatchNamespace("c"))))
  }

  it should "escape '.'(dot)" in {
    Path.from("crawl.host.www\\.abc\\.com") should be (Path(List(MatchNamespace("crawl"), MatchNamespace("host"), MatchNamespace("www.abc.com"))))
  }

  it should "build MatchValue for string values" in {
    Path.from("[ crawl.host.delay = \"10\"]") should be(Path(List(Match(Equals(Path.from("crawl.host.delay"),"10")))))
    Path.from("crawl.host.[site.name=\"www.abc.com\"].delay") should be(Path(List(MatchNamespace("crawl"),MatchNamespace("host"),Match(Equals(Path.from("site.name"),"www.abc.com")),MatchNamespace("delay"))))
  }

  it should "build MatchValue for integer values" in {
    Path.from("[crawl.host.delay = 10]") should be(Path(List(Match(Equals(Path.from("crawl.host.delay"),10)))))
  }

  it should "build MatchValue for boolean values" in {
    Path.from("[crawl.host.useAjax = true]") should be(Path(List(Match(Equals(Path.from("crawl.host.useAjax"), true)))))
    Path.from("[crawl.host.useAjax = false]") should be(Path(List(Match(Equals(Path.from("crawl.host.useAjax"), false)))))
  }

  it should "build MatchValue for double values" in {
    Path.from("[crawl.host.complexity = 1.9]") should be(Path(List(Match(Equals(Path.from("crawl.host.complexity"), 1.9)))))
    Path.from("[crawl.host.complexity = .9]") should be(Path(List(Match(Equals(Path.from("crawl.host.complexity"), .9)))))
  }

  it should "build contains Value" in {
    Path.from("[ sites.tags @ 10]") should be(Path(List(Match(Contains(Path.from("sites.tags"),10)))))
    Path.from("sites.[tags @ \"www.abc.com\"].crawl") should be(Path(List(MatchNamespace("sites"), Match(Contains(Path.from("tags"),"www.abc.com")),MatchNamespace("crawl"))))
  }

  it should "build ! Value" in {
    Path.from("[!crawl.host.complexity = 1.9]") should be(Path(List(Match(Not(Equals(Path.from("crawl.host.complexity"), 1.9))))))
    Path.from("sites.[!(tags @ \"www.abc.com\")].crawl") should be(Path(List(MatchNamespace("sites"), Match(Not(Contains(Path.from("tags"),"www.abc.com"))),MatchNamespace("crawl"))))
  }

  it should "build and Value" in {
    Path.from("sites.[tags @ \"www.abc.com\" & crawl.host.complexity = 1.9].crawl") should be(Path(List(MatchNamespace("sites"),
      Match(And(Contains(Path.from("tags"),"www.abc.com"), Equals(Path.from("crawl.host.complexity"), 1.9))),MatchNamespace("crawl"))))
  }

  it should "build or Value" in {
    Path.from("sites.[tags @ \"microsoft\" | name=1].crawl") should be (Path(List(MatchNamespace("sites"),
      Match(Or(Contains(Path.from("tags"),"microsoft"), Equals(Path.from("name"), 1))), MatchNamespace("crawl"))))
  }

  it should "build exists value" in {
    Path.from("sites.[somesite.proxy?]") should be(Path(List(MatchNamespace("sites"), Match(Exists(Path.from("somesite.proxy"))))))
  }

  it should "build refers value" in {
    Path.from("sites.[somesite.proxy#123]") should be(Path(List(MatchNamespace("sites"), Match(Refers(Path.from("somesite.proxy"), 123)))))
    Path.from("sites.[#123]") should be(Path(List(MatchNamespace("sites"), Match(Refers(Path.from(""), 123)))))
  }

  it should "build parenthesised Value" in {
    Path.from("[(crawl.host.complexity = 1.9)]") should be(Path(List(Match(Equals(Path.from("crawl.host.complexity"), 1.9)))))
    Path.from("[( sites.tags @ 10 )]") should be(Path(List(Match(Contains(Path.from("sites.tags"),10)))))
    Path.from("[ (crawl.host.delay = 10 )]") should be(Path(List(Match(Equals(Path.from("crawl.host.delay"),10)))))
  }

  it should "build at correct precedence" in {
    Path.from("sites.[name=\"www.abc.com\" | !tags @ \"microsoft\" & crawl.host.complexity = 1.9 ]") should be(Path(List(MatchNamespace("sites"),
      Match(Or(
        Equals(Path.from("name"), "www.abc.com"),
        And(Not(Contains(Path.from("tags"), "microsoft")), Equals(Path.from("crawl.host.complexity"), 1.9))
      )))))

    Path.from("sites.[(name=\"www.abc.com\" | !tags @ \"microsoft\") & crawl.host.complexity = 1.9 ]") should be(Path(List(MatchNamespace("sites"),
      Match(And(
        Or(Equals(Path.from("name"), "www.abc.com"), Not(Contains(Path.from("tags"), "microsoft"))),
        Equals(Path.from("crawl.host.complexity"), 1.9))
      ))))
  }

  it should "build MatchIndex with specified index" in {
    Path.from("crawl.host.[0].site.name") should be (Path(List(MatchNamespace("crawl"), MatchNamespace("host"), MatchIndex(0), MatchNamespace("site"), MatchNamespace("name"))))
  }

  it should "build select keys from path component" in {
    Path.from("host.{crawl, seed}") should be (Path(List(MatchNamespace("host"), SelectKeys(Set("crawl", "seed")))))
  }

  it should "convert path components back to string form" in {
    MatchNamespace("a").toString should be("a")
    Match(Equals(Path.from("crawl.host"), "www.abc.com")).toString should be("[ crawl.host = \"www.abc.com\" ]")
    Match(Equals(Path.from("crawl.host.delay"), 10)).toString should be("[ crawl.host.delay = 10 ]")
    Match(Equals(Path.from("crawl.host.useAjax"), true)).toString should be("[ crawl.host.useAjax = true ]")
    Match(Equals(Path.from("crawl.host.useAjax"), false)).toString should be("[ crawl.host.useAjax = false ]")
    Match(Equals(Path.from("crawl.host.complexity"), 1.9)).toString should be("[ crawl.host.complexity = 1.9 ]")
    SelectKeys(Set("crawl", "seed")).toString should be("{crawl, seed}")
  }

  it should "verify whether a path affects another path" in {
    Path.from("crawl.host.www\\.abc\\.com").affects(Path.from("crawl")) should be (true)
    Path.from("crawl").affects(Path.from("crawl.host.www\\.abc\\.com")) should be (true)
    Path.from("crawl.host.[site.name=\"www.abc.com\"]").affects(Path.from("crawl.host")) should be (true)
    Path.from("crawl.host").affects(Path.from("crawl.host.[site.name=\"www.abc.com\"]")) should be (true)
    Path.from("crawl.host").affects(Path.from("crawl.host.*")) should be (true)
    Path.from("crawl").affects(Path.from("host.www\\.abc\\.com")) should be (false)
  }
}

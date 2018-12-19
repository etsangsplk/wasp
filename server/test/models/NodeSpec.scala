package models

import com.indix.wasp.models.User
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import utils.JsonUtils

class NodeSpec extends FlatSpec with Matchers with BeforeAndAfterEach {
  val user = User("TestAdmin", "test@indix.com")
  val user1 = User("Indix User 1", "user1@indix.com")
  val user2 = User("Indix User 2", "user2@indix.com")
  val user3 = User("Indix User 3", "user3@indix.com")
  val timestamp = DateTime.now().getMillis
  implicit val nodeMeta1 = NodeMeta(user1, timestamp)
  val nodeMeta2 = NodeMeta(user2, timestamp)
  val nodeMeta3 = NodeMeta(user3, timestamp)
  implicit val references = new References(Map.empty)
  val confValue = JsonUtils.fromJson(
    """
      |{
      | "key1" : {
      |   "key2" : ["value1", "value2"],
      |   "key3" : 12.34,
      |   "key4" : "value4"
      | }
      |}
    """.stripMargin)

  val testCrawlerConf = JsonUtils.fromJson(
    """
      |{
      |   "prod" : {
      |     "crawler" : [
      |       {
      |         "host" : "www.abc.com",
      |         "crawl-delay" : 10
      |       },
      |       {
      |         "host" : "www.xyz.com",
      |         "crawl-delay" : 7
      |       }
      |     ]
      |   }
      |}
    """.stripMargin)

  val testCrawlSeedConfValue = JsonUtils.fromJson(
    """
      |{
      |	"sites" : {
      |		 "www.domain.com" : {
      |		   "tags" : [
      |		     "microsoft",
      |		     "indix"],
      |		   "seed" : {
      |		     "type" : "config",
      |		     "use-sitemap" : true
      |		   },
      |		   "analyze" : true,
      |		   "crawl" : {
      |		     "type" : "useBrowserAgent",
      |		     "crawlDelay" : 3000,
      |		     "basic": {
      |		       "noOfAgents" : 2
      |		     }
      |		    }
      |		  },
      |		 "www.domain2.com" : {
      |		   "tags" : [
      |		     "drugstore",
      |		     "indix"],
      |		   "seed" : {
      |		     "type" : "config",
      |		     "use-sitemap" : true
      |		   },
      |		   "analyze" : true,
      |		   "crawl" : {
      |		     "type" : "useBrowserAgent"
      |		   }
      |		 }
      |	}
      |}
    """.stripMargin)

  override def beforeEach(): Unit = references.clear

  def addLink(initial: Node, path: Path, sourcePath: Path)(implicit references: References): (Node, Long) ={
    var root = initial
    val key = root.extractRef(sourcePath)
    root = root.add(new Reference(key), user, timestamp)(sourcePath)
    root = root.add(new Reference(key), user, timestamp)(path)
    (root, key)
  }

  "Tree" should "be value in case of primitive text/integer/float" in {
    Tree(JsonUtils.fromJson("1234")) should be(Value(1234))
    Tree(JsonUtils.fromJson("\"1234\"")) should be(Value("1234"))
    Tree(JsonUtils.fromJson("12.34")) should be(Value(12.34))
  }

  it should "create ListValue from list of items" in {
    Tree(JsonUtils.fromJson("[1,2,3]")) should be(ListValue(List(Value(1),Value(2),Value(3))))
  }

  it should "create a Namespace Node from Map" in {
    Tree(JsonUtils.fromJson("""{"key" : "value"}""")) should be(Namespace(Map("key" -> Value("value"))))
    Tree(confValue) should be (Namespace(Map(
      "key1" -> Namespace(Map(
        "key2" -> ListValue(List(Value("value1"), Value("value2"))),
        "key3" -> Value(12.34),
        "key4" -> Value("value4"))
      ))))
  }

  it should "have correct meta info for its children" in {
    Tree(confValue)(nodeMeta1).traverse(Path.from("key1.key2")).get.getNodeMeta should be (nodeMeta1)
    Tree(confValue)(nodeMeta2).traverse(Path.from("key1.key3")).get.getNodeMeta should be (nodeMeta2)
    Tree(confValue)(nodeMeta1).traverse(Path.from("key1.key4")).get.getNodeMeta should be (nodeMeta1)
    Tree(confValue)(nodeMeta2).traverse(Path.from("key1.key2.[1]")).get.getNodeMeta should be (nodeMeta2)
  }

  "Node" should "be the same element on traversal with empty Path" in {
    Value(1234).traverse(Path(Nil)) should be (Some(Value(1234)))
    ListValue(List(Value(1), Value(2))).traverse(Path(Nil)) should be (Some(ListValue(List(Value(1), Value(2)))))
    Tree(confValue).traverse(Path(Nil)) should be (Some(Tree(confValue)))
  }

  it should "be traversed to the Node specified by the Path" in {
    Tree(confValue).traverse(Path.from("key1.key2")) should be (Some(ListValue(List(Value("value1"), Value("value2")))))
    Tree(confValue).traverse(Path.from("key1.key3")) should be (Some(Value(12.34)))
  }

  it should "fail on traversal of Value and ListValue with non-empty Paths" in {
    Value(1234).traverse(Path.from("a"))  should be(None)
    ListValue(List(Value(1234))).traverse(Path.from("a")) should be(None)
    ListValue(List(Value(1), Value(2), Value(3))).traverse(Path.from("[3]")) should be(None)
  }

  it should "fail while traversing invalid Path" in {
    Tree(confValue).traverse(Path.from("key1.key4.key5")) should be(None)
  }

  it should "add all non-existing Namespaces while adding a value to the leaf" in {
    Namespace().add(Value(1234), user, timestamp)(Path.from("a.b")) should be (Namespace(Map("a" -> Namespace(Map("b" -> Value(1234))))))
    Tree(confValue).add(Namespace(Map("key4" -> Value("some-value"))), user1, timestamp)(Path.from("key1.key3")).traverse(Path.from("key1.key3.key4")) should be (Some(Value("some-value")))
  }

  it should "fail on trying to add values to ListValue/Value/non-existing path" in {
    the [RuntimeException] thrownBy {
      Value(1234).add(Namespace(), user, timestamp)(Path.from("a"))
    } should be(IllegalOperationException(Path.from("a"), "Trying to add value to value"))

    the [RuntimeException] thrownBy {
      ListValue(List(Value(1234))).add(Namespace(), user, timestamp)(Path.from("a"))
    } should be(IllegalOperationException(Path.from("a"), "Trying to access in lists"))


    the [RuntimeException] thrownBy {
      Tree(confValue).add(Namespace(), user, timestamp)(Path.from("key1.key3.key4"))
    } should be(IllegalOperationException(Path.from("key4"), "Trying to add value to value"))

    the [RuntimeException] thrownBy {
      ListValue(List(Namespace(), Value(1234))).add(Value(1111), user, timestamp)(Path.from("[4]"))
    } should be(PathNotFoundException(Path.from("[4]")))
  }

  it should "replace the existing Value on calling add" in {
    Tree(confValue).add(Value(1000), user, timestamp)(Path.from("key1.key3")).traverse(Path.from("key1.key3")) should be (Some(Value(1000)))
    Value(1000).add(Value(2000), user, timestamp)(Path(Nil)) should be (Value(2000))
    ListValue(List(Value(1000))).add(Value(2000), user, timestamp)(Path(Nil)) should be (Value(2000))
    ListValue(List(Value(1),Value(2),Value(3))).add(Value(1000), user, timestamp)(Path.from("[2]")).traverse(Path.from("[2]")) should be (Some(Value(1000)))
    ListValue(List(Value(1),Value(2),Value(3))).add(Value(1000), user, timestamp)(Path.from("[3]")) should be (ListValue(List(Value(1),Value(2),Value(3),Value(1000))))
  }

  it should "delete from an existing path in config" in {
    Tree(confValue).delete(user, timestamp)(Path.from("key1")) should be (Namespace())
    Tree(confValue).delete(user, timestamp)(Path.from("key1.key2")) should be (Namespace(Map("key1" -> Namespace(Map("key3" -> Value(12.34), "key4" -> Value("value4"))))))
  }

  it should "fail to delete from non-existing path" in {
    the [RuntimeException] thrownBy {
      Tree(confValue).delete(user, timestamp)(Path.from("key1.key5"))
    } should be(PathNotFoundException(Path.from("key5")))
  }

  it should "fail to delete from Value/ListValue" in {
    the [RuntimeException] thrownBy {
      Value(1000).delete(user, timestamp)(Path(Nil))
    } should be(IllegalOperationException(Path(Nil),"Trying to remove value from value"))

    the [RuntimeException] thrownBy {
      Value(1000).delete(user, timestamp)(Path.from("a.b"))
    } should be(IllegalOperationException(Path.from("a.b"),"Trying to remove value from value"))

    the [RuntimeException] thrownBy {
      ListValue(List(Value(1000))).delete(user, timestamp)(Path.from("a.b"))
    } should be(IllegalOperationException(Path.from("a.b"),"Trying to access in lists"))

    the [RuntimeException] thrownBy {
      ListValue(List(Value(1), Value(2))).delete(user, timestamp)(Path.from("[2]"))
    } should be(PathNotFoundException(Path.from("[2]")))
  }

  it should "traverse/add/delete through ListValue matching the path" in {
    val conf = Tree(JsonUtils.fromJson(
      """
        |{
        | "key1" : {
        |   "key2" : [{
        |       "key11" : {
        |           "key12" : "10",
        |           "key13" : "gotcha"
        |       }},
        |       {
        |       "key11" : {
        |           "key12" : "20",
        |           "key13" : "gotcha2"
        |       }}],
        |   "key3" : 12.34
        | }
        |}
      """.stripMargin))

    val path: Path = Path.from("key1.key2.[key11.key12 = \"10\"]")
    val toAdd: Namespace = Namespace(Map("key11" -> Namespace(Map("key12" -> Value("100")))))

    conf.traverse(path) should be (Some(Namespace(Map("key11" -> Namespace(Map("key12" -> Value("10"),"key13" -> Value("gotcha")))))))
    conf.add(toAdd, user, timestamp)(path).traverse(Path.from("key1.key2.[key11.key12 = \"100\"]")) should be(Some(toAdd))
    conf.traverse(Path.from("key1.key2.[0].key11.key12")) should be(Some(Value("10")))
    conf.add(toAdd, user, timestamp)(Path.from("key1.key2.[0]")).traverse(Path.from("key1.key2.[0]")) should be(Some(toAdd))
    conf.delete(user, timestamp)(path).traverse(Path.from("key1.key2")) should be (Some(ListValue(List(Namespace(Map("key11" -> Namespace(Map("key12" -> Value("20"), "key13" -> Value("gotcha2")))))))))
    conf.delete(user, timestamp)(Path.from("key1.key2.[0]")).traverse(Path.from("key1.key2.[0]")) should be (Some(Namespace(Map("key11" -> Namespace(Map("key12" -> Value("20"), "key13" -> Value("gotcha2")))))))
  }

  it should "apply defaults in case any exist" in {
    Namespace(Map.empty).addDefault(Path(Nil), user, timestamp)(Path.from("key"),Value(100)).compute(Path.from("key")) should be (Some(Value(100)))
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key5"),Value(1000)).traverse(Path.from("key1.key5")) should be(None)
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key5"),Value(1000)).compute(Path.from("key1.key5")) should be(Some(Value(1000)))
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key5"),Value(1000)).compute(Path.from("key1")) should be(Some(Namespace(Map(
      "key2" -> ListValue(List(Value("value1"), Value("value2"))),
      "key3" -> Value(12.34),
      "key4" -> Value("value4"),
      "key5" -> Value(1000)
    ))))
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key5"),Namespace(Map("key3" -> Namespace(Map("key6" -> Value(1000)))))).compute(Path.from("key1.key5.key3.key6")) should be(Some(Value(1000)))
    Tree(confValue).addDefault(Path.from("key1.key5"), user, timestamp)(Path.from("key10"),Value(1000)).compute(Path.from("key1")) should be(Some(Namespace(Map(
      "key2" -> ListValue(List(Value("value1"), Value("value2"))),
      "key3" -> Value(12.34),
      "key4" -> Value("value4"),
      "key5" -> Namespace(Map("key10" -> Value(1000)))
    ))))
  }

  it should "apply defaults to sub-trees only if the value doesn't already exist" in {
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key3"), Value("Blonde")).compute(Path.from("key1.key3")) should be (Some(Value(12.34)))
  }

  it should "apply defaults on wildcard paths" in {
    val testParserConf = JsonUtils.fromJson(
      """
        |{
        | "prod" : {
        |   "parser" :{
        |     "www.abc.com" : {
        |       "selector" : "div.arg",
        |       "total"    : 5,
        |       "volumes" : {
        |         "1" : "martin odersky",
        |         "2" : "heather miller"
        |       }
        |     },
        |     "www.def.com" : {
        |       "selector" : "div.two-args"
        |     }
        |   },
        |   "proxies" : [
        |     {
        |       "host" : "proxy1.tv",
        |       "port" : 9090
        |     },
        |     {
        |       "host" : "proxy2.tv",
        |       "port" : 11111,
        |       "user" : "Rick Hickey",
        |       "pass" : "pants"
        |     }
        |   ]
        | }
        |}
      """.stripMargin
    )
    val defaultProxyConf = JsonUtils.fromJson(
      """
        |{
        | "user" : "",
        | "pass" : ""
        |}
      """.stripMargin
    )
    val expectedProxy1Conf = JsonUtils.fromJson(
      """
        |{
        | "host" : "proxy1.tv",
        | "port" : 9090,
        | "user" : "",
        | "pass" : ""
        |}
      """.stripMargin
    )
    val expectedProxy2Conf = JsonUtils.fromJson(
      """
        |{
        |  "host" : "proxy2.tv",
        |  "port" : 11111,
        |  "user" : "Rick Hickey",
        |  "pass" : "pants"
        |}
      """.stripMargin
    )
    val defaultConf = JsonUtils.fromJson(
      """
        |{
        | "selector" : "none",
        | "params" : "None",
        | "custom-tagger" : false,
        | "total" : 1,
        | "volumes" : {
        |   "con" : "job"
        | },
        | "languages" : []
        |}
      """.stripMargin
    )
    val expectedDefConf = JsonUtils.fromJson(
      """
        |{
        |   "selector" : "div.two-args",
        |   "params" : "None",
        |   "custom-tagger" : false,
        |   "total" : 1,
        |   "volumes" : {
        |     "con" : "job"
        |   },
        |   "languages" : []
        |}
      """.stripMargin
    )
    val expectedAbcConf = JsonUtils.fromJson(
      """
        |{
        |   "selector" : "div.arg",
        |   "params" : "None",
        |   "custom-tagger" : false,
        |   "total" : 5,
        |   "volumes" : {
        |     "1" : "martin odersky",
        |     "2" : "heather miller",
        |     "con" : "job"
        |   },
        |   "languages" : []
        |}
      """.stripMargin
    )
    Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("*.key5"),Value(1000)).compute(Path.from("key1.key5")) should be(Some(Value(1000)))
    Tree(confValue).add(Namespace(), user, timestamp)(Path.from("key1.key5")).addDefault(Path(Nil), user, timestamp)(Path.from("key1.*.key10"),Value(1000)).compute(Path.from("key1.key5")) should be(Some(Namespace(Map("key10" -> Value(1000)))))
    Tree(testParserConf).addDefault(Path.from("prod.parser"), user, timestamp)(Path.from("*"), Tree(defaultConf)).compute(Path.from("prod.parser.www\\.abc\\.com")) should be (Some(Tree(expectedAbcConf)))
    Tree(testParserConf).addDefault(Path.from("prod.parser"), user, timestamp)(Path.from("*"), Tree(defaultConf)).compute(Path.from("prod.parser.www\\.def\\.com")) should be (Some(Tree(expectedDefConf)))
    Tree(testParserConf).addDefault(Path.from("prod.parser.*"), user, timestamp)(Path(Nil), Tree(defaultConf)).compute(Path.from("prod.parser.www\\.def\\.com")) should be (Some(Tree(expectedDefConf)))
    Tree(testParserConf).addDefault(Path.from("prod.parser"), user, timestamp)(Path.from("www\\.abc\\.com"), Tree(defaultConf)).compute(Path.from("prod.parser.www\\.abc\\.com")) should be (Some(Tree(expectedAbcConf)))
    Tree(testParserConf).addDefault(Path.from("prod.proxies.*"), user, timestamp)(Path(Nil), Tree(defaultProxyConf)).compute(Path.from("prod.proxies.[host=\"proxy1.tv\"]")) should be(Some(Tree(expectedProxy1Conf)))
    Tree(testParserConf).addDefault(Path.from("prod.proxies.*"), user, timestamp)(Path(Nil), Tree(defaultProxyConf)).compute(Path.from("prod.proxies.[host=\"proxy2.tv\"]")) should be(Some(Tree(expectedProxy2Conf)))
  }

  it should "add to all nodes in the listvalue" in {
    ListValue(List(Namespace(),Namespace(Map("y" -> Value(10))))).add(Value(100), user, timestamp)(Path.from("*")) should be(ListValue(List(Value(100),Value(100))))
    ListValue(List(Namespace(),Namespace(Map("y" -> Value(10))))).add(Value(100), user, timestamp)(Path.from("*.x")) should be(ListValue(List(
      Namespace(Map("x" -> Value(100))),Namespace(Map("x" -> Value(100),"y" -> Value(10)))
    )))
    ListValue(List(Namespace(),Namespace())).add(Value(100), user, timestamp)(Path.from("[0]")) should be(ListValue(List(Value(100), Namespace())))
    ListValue(List(Namespace(),Namespace())).add(Value(100), user, timestamp)(Path.from("[2]")) should be(ListValue(List(Namespace(), Namespace(), Value(100))))
  }

  it should "remove element at a given index in a list" in {
    Tree(confValue).delete(user, timestamp)(Path.from("key1.key2.[0]")).traverse(Path.from("key1.key2")) should be(Some(ListValue(List(Value("value2")))))
  }

  it should "remove entries in all the nodes in a list or a namespace" in {
    Tree(confValue).delete(user, timestamp)(Path.from("key1.key2.*")).traverse(Path.from("key1.key2")) should be(Some(ListValue(Nil)))
    Tree(confValue).delete(user, timestamp)(Path.from("*.key2")) should be (Namespace(Map("key1" -> Namespace(Map("key3" -> Value(12.34),"key4" -> Value("value4"))))))
  }

  it should "traverse through path containing wildcard" in {
    Tree(confValue).traverse(Path.from("*.key2")) should be (Some(Namespace (Map("key1" -> ListValue(List(Value("value1"), Value("value2")))))))
    Tree(confValue).traverse(Path.from("*.*")) should be (Some(Tree(confValue)))
    Tree(confValue).traverse(Path.from("key1.key2.*")) should be (Some(ListValue(List(Value("value1"), Value("value2")))))
    Tree(confValue).traverse(Path.from("*.key2.*")) should be (Some(Namespace(Map("key1" -> ListValue(List(Value("value1"), Value("value2")))))))

    Tree(testCrawlerConf).traverse(Path.from("""prod.crawler.[ host = "www.abc.com"].*""")) should be (Some(Namespace(Map("host" -> Value("www.abc.com"),"crawl-delay" -> Value(10)))))
    Tree(testCrawlerConf).traverse(Path.from("""prod.crawler.[ 0 ].*""")) should be (Some(Namespace(Map("host" -> Value("www.abc.com"),"crawl-delay" -> Value(10)))))
  }

  it should "traverse through path containing select keys" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.*.{analyze, crawl}")) should be
    (Some(Namespace (Map("www.domain.com" -> Namespace(Map("analyze" -> Value(true),
      "crawl" -> Namespace(Map("type"->Value("useBrowserAgent"), "crawlDelay" -> Value(3000),
        "basic" -> Namespace(Map("noOfAgents" -> Value("2")))))))))))
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.{www\\.domain\\.com, crawl}.analyze")) should be (Some(Namespace (Map("www.domain.com" -> Value(true)))))
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.www\\.domain\\.com.{analyze, crawl}")) should be (Some(Namespace(Map("analyze" -> Value(true),
      "crawl" -> Namespace(Map("type"->Value("useBrowserAgent"), "crawlDelay" -> Value(3000),
        "basic" -> Namespace(Map("noOfAgents" -> Value(2)))))))))
  }

  it should "add node at all select keys" in {
    Namespace().add(Value(1000), user, timestamp)(Path(List(SelectKeys(Set("key1","key2"))))) should be
    (Namespace(Map("key1" -> Value(1000), "key2" -> Value(1000))))

    val conf = Tree(testCrawlSeedConfValue).add(Value("us-proxy"), user, timestamp)(Path.from("www\\.domain\\.com.{seed, crawl}.proxy"))
    conf.traverse(Path.from("www\\.domain\\.com.seed.proxy")) should be (Some(Value("us-proxy")))
    conf.traverse(Path.from("www\\.domain\\.com.crawl.proxy")) should be (Some(Value("us-proxy")))

    the [RuntimeException] thrownBy {
      ListValue(List()).add(Value(1000), user, timestamp)(Path(List(SelectKeys(Set("key1","key2")))))
    } should be(IllegalOperationException(Path.from("{key1,key2}"), "Trying to access in lists"))

    the [RuntimeException] thrownBy {
      Value(1000).add(Value(1000), user, timestamp)(Path(List(SelectKeys(Set("key1","key2")))))
    } should be(IllegalOperationException(Path.from("{key1,key2}"), "Trying to add value to value"))
  }

  it should "overwrite existing value on adding at existing node" in {
    Tree(testCrawlSeedConfValue).add(Value(false), user, timestamp)(Path.from("www\\.domain\\.com.{seed, crawl}.use-sitemap"))
      .traverse(Path.from("www\\.domain\\.com.seed.use-sitemap")) should be (Some(Value(false)))
  }

  it should "delete nodes at all select keys" in {
    Tree(testCrawlSeedConfValue).delete(user, timestamp)(Path.from("sites.www\\.domain\\.com.{seed, crawl}")) should be (Namespace(Map("sites" ->
      Namespace(Map("www.domain.com" -> Namespace(Map("analyze" -> Value(true), "tags" -> ListValue(List(Value("microsoft"), Value("indix"))))),
        "www.domain2.com" -> Namespace(Map("tags" -> ListValue(List(Value("drugstore"), Value("indix"))),
          "seed" ->
            Namespace(Map("type" -> Value("config"),
              "use-sitemap" -> Value(true))),
          "analyze" -> Value(true),
          "crawl" -> Namespace(Map("type" -> Value("useBrowserAgent")))))
      )))))

    Tree(testCrawlSeedConfValue).delete(user, timestamp)(Path.from("sites.www\\.domain\\.com.{seed, crawl}.type")) should be (Namespace(Map("sites" ->
      Namespace(Map("www.domain.com" -> Namespace(
        Map("tags" -> ListValue(List(Value("microsoft"), Value("indix"))),
          "seed" -> Namespace(Map("use-sitemap" -> Value(true))),
          "analyze" -> Value(true),
          "crawl" -> Namespace(Map("crawlDelay" -> Value(3000),
            "basic" -> Namespace(Map("noOfAgents" -> Value(2))))
          ))),
        "www.domain2.com" -> Namespace(Map("tags" -> ListValue(List(Value("drugstore"), Value("indix"))),
          "seed" ->
            Namespace(Map("type" -> Value("config"),
              "use-sitemap" -> Value(true))),
          "analyze" -> Value(true),
          "crawl" -> Namespace(Map("type" -> Value("useBrowserAgent")))))
      )))))
  }

  it should "add defaults at node(s) specified by paths containing wildcards" in {
    Tree(confValue).addDefault(Path.from("*"), user, timestamp)(Path.from("key10"), Value(1000)).compute(Path.from("key1.key10")) should be (Some(Value(1000)))
    Tree(testCrawlerConf).addDefault(Path.from("prod.crawler.*"), user, timestamp)(Path.from("user-agent"), Value("abcbot")).compute(Path.from("""prod.crawler.[host="www.abc.com"].user-agent""")) should be (Some(Value("abcbot")))
  }

  it should "add defaults at node(s) specified by paths at select keys" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}"), user, timestamp)(Path.from("use-sitemap"), Value(false))
    conf.compute(Path.from("sites.www\\.domain\\.com.seed.use-sitemap")) should be (Some(Value(true)))
    the [RuntimeException] thrownBy {
      conf.compute(Path.from("sites.www\\.domain\\.com.analyze.use-sitemap"))
    } should be(PathNotFoundException(Path.from("use-sitemap")))
    conf.compute(Path.from("sites.www\\.domain\\.com.crawl.use-sitemap")) should be (Some(Value(false)))
  }


  it should "add defaults at node(s) specified by paths containing select keys" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}.basic"), user, timestamp)(Path.from("proxy"), Value("ca-proxy"))
    conf.compute(Path.from("sites.www\\.domain\\.com.seed.basic.proxy")) should be (Some(Value("ca-proxy")))
    the [RuntimeException] thrownBy {
      conf.compute(Path.from("sites.www\\.domain\\.com.analyze.proxy"))
    } should be(PathNotFoundException(Path.from("proxy")))
    conf.compute(Path.from("sites.www\\.domain\\.com.crawl.basic.proxy")) should be (Some(Value("ca-proxy")))
  }

  it should "delete defaults at node(s) specified by paths containing select keys" in {
    val initialConf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}"), user, timestamp)(Path.from("crawlDelay"), Value(1000))
    val conf = initialConf.deleteDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}"), user, timestamp)(Path.from("crawlDelay"))
    conf.compute(Path.from("sites.www\\.domain\\.com.seed.crawlDelay")) should be (None)
    conf.compute(Path.from("sites.www\\.domain\\.com.crawl.crawlDelay")) should be (Some(Value(3000)))
  }

  it should  "add default at each individual select keys" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}"), user, timestamp)(Path.from("proxy"), Value("ca-proxy"))
    conf.deleteDefault(Path.from("sites.www\\.domain\\.com.seed"), user, timestamp)(Path.from("proxy")).compute(Path.from("sites.www\\.domain\\.com.crawl.proxy")) should be (Some(Value("ca-proxy")))
  }

  it should "properly perform compute on select keys" in {
    val defaultConf = JsonUtils.fromJson(
      """
        | {
        |   "noOfAgents": 1,
        |   "geo" :"US"
        | }
      """.stripMargin
    )
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}"), user, timestamp)(Path.from("basic"), Tree(defaultConf))
    conf.compute(Path.from("sites.www\\.domain\\.com.{seed, crawl}.basic")) should be (Some(Namespace(
      Map("seed" -> Namespace(Map("noOfAgents" -> Value(1), "geo" -> Value("US"))),
        "crawl" -> Namespace(Map("noOfAgents" -> Value(2), "geo" -> Value("US")))))))
  }

  it should "properly perform compute on path containing select keys" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.{seed, crawl}.basic"), user, timestamp)(Path.from("noOfAgents"), Value(1))
    conf.compute(Path.from("sites.www\\.domain\\.com.{seed, crawl}.basic.noOfAgents")) should be (Some(Namespace(
      Map("seed" ->  Value(1),
        "crawl" -> Value(2)))))
  }

  it should "perform traverse on path containing contains predicate" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[tags @ \"indix\"].tags")) should be (Some(Namespace(Map("www.domain.com" -> ListValue(List(Value("microsoft"), Value("indix"))),
      "www.domain2.com" -> ListValue(List(Value("drugstore"), Value("indix")))))))
  }

  it should "properly perform compute on path containing contains predicate" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain\\.com.crawl.basic"), user, timestamp)(Path.from("noOfAgents"), Value(1))
    conf.compute(Path.from("sites.[tags @ \"indix\"].crawl.basic.noOfAgents")) should be (Some(Namespace(Map(
      "www.domain.com" ->  Value(2)
    ))))
  }

  it should "properly perform add on path containing contains predicate" in {
    Tree(testCrawlSeedConfValue).add(Value(2)(nodeMeta2), user2, timestamp)(Path.from("sites.[tags @ \"drugstore\"].tier"))
      .traverse(Path.from("sites.*.tier")) should be (Some(Namespace(Map(
      "www.domain2.com" -> Value(2)
    ))))
  }

  it should "properly perform delete on path containing contains predicate" in {
    Tree(testCrawlSeedConfValue).delete(user2, timestamp)(Path.from("sites.[tags @ \"microsoft\"].crawl"))
      .traverse(Path.from("sites.*.crawl")) should be (Some(Namespace(Map(
      "www.domain2.com" -> Namespace(Map("type" -> Value("useBrowserAgent")),Defaults(Map()))),Defaults(Map()))))
  }

  it should "properly perform traverse on path containing not predicate" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[! tags @ \"microsoft\"].tags")) should be (Some(Namespace(
      Map("www.domain2.com" -> ListValue(List(Value("drugstore"), Value("indix")))))))
  }

  it should "perform traverse on path containing and predicate" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[tags @ \"indix\" & crawl.crawlDelay=3000].tags")) should be (Some(Namespace(Map(
      "www.domain.com" -> ListValue(List(Value("microsoft"), Value("indix")))))))
  }

  it should "perform traverse on path containing or predicate" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[tags @ \"drugstore\" | crawl.crawlDelay=3000].tags")) should be (Some(Namespace(Map(
      "www.domain.com" -> ListValue(List(Value("microsoft"), Value("indix"))),
      "www.domain2.com" -> ListValue(List(Value("drugstore"), Value("indix")))))))
  }

  it should "perform traverse on parenthesised conditionals" in {
    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[!tags @ \"drugstore\" | !crawl.basic.noOfAgents = 2 & !crawl.crawlDelay=3000].tags")) should be (Some(Namespace(Map(
      "www.domain.com" -> ListValue(List(Value("microsoft"), Value("indix"))),
      "www.domain2.com" -> ListValue(List(Value("drugstore"), Value("indix"))))))
    )

    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[((!tags @ \"drugstore\") | (!crawl.basic.noOfAgents = 2)) & (!crawl.crawlDelay=3000)].tags")) should be (Some(Namespace(Map(
      "www.domain2.com" -> ListValue(List(Value("drugstore"), Value("indix")))))))

    Tree(testCrawlSeedConfValue).traverse(Path.from("sites.[!((tags @ \"drugstore\") | ((!crawl.basic.noOfAgents = 2) & (!crawl.crawlDelay=3000)))].tags")) should be (Some(Namespace(Map(
      "www.domain.com" -> ListValue(List(Value("microsoft"), Value("indix")))))))
  }

  it should "perform compute on path containing boolean logic" in {
    val conf = Tree(testCrawlSeedConfValue).addDefault(Path.from("sites.www\\.domain2\\.com.crawl"), user, timestamp)(Path.from("crawlDelay"), Value(1000))
    conf.compute(Path.from("sites.[((!tags @ \"drugstore\") | (!crawl.basic.noOfAgents = 2)) & (!crawl.crawlDelay=3000)].crawl.crawlDelay")) should be (Some(Namespace(Map(
      "www.domain2.com" ->  Value(1000)
    ))))

    conf.compute(Path.from("sites.[!((tags @ \"drugstore\") | ((!crawl.basic.noOfAgents = 2) & (!crawl.crawlDelay=3000)))].crawl.crawlDelay")) should be (Some(Namespace(Map(
      "www.domain.com" ->  Value(3000)
    ))))
  }

  it should "perform add on path containing boolean logic" in {
    Tree(testCrawlSeedConfValue).add(Value(2)(nodeMeta2), user2, timestamp)(Path.from("sites.[((!tags @ \"drugstore\") | (!crawl.basic.noOfAgents = 2)) & (!crawl.crawlDelay=3000)].tier"))
      .traverse(Path.from("sites.*.tier")) should be (Some(Namespace(Map(
      "www.domain2.com" -> Value(2)))))
  }

  it should "perform delete on path containing boolean logic" in {
    Tree(testCrawlSeedConfValue).delete(user2, timestamp)(Path.from("sites.[!((tags @ \"drugstore\") | ((!crawl.basic.noOfAgents = 2) & (!crawl.crawlDelay=3000)))].crawl"))
      .traverse(Path.from("sites.*.crawl")) should be (Some(Namespace(Map(
      "www.domain2.com" -> Namespace(Map("type" -> Value("useBrowserAgent")),Defaults(Map()))),Defaults(Map()))))
  }

  it should "delete existing defaults from Node(s)" in {
    val initial = Tree(confValue).addDefault(Path(Nil), user, timestamp)(Path.from("key1.key5"),Value(1000))
    initial.deleteDefault(Path(Nil), user, timestamp)(Path.from("key1")).compute(Path.from("key1.key5")) should be (Some(Value(1000))) //Doesn't delete anything as the defaults contains just the key 'key1.key4' and not 'key1'
    initial.deleteDefault(Path(Nil), user, timestamp)(Path.from("key1.key5")).compute(Path.from("")) should be (Some(Tree(confValue)))
    Tree(testCrawlerConf)
      .addDefault(Path.from("prod.crawler.*"), user, timestamp)(Path.from("user-agent"), Value("abcbot"))
      .deleteDefault(Path.from("prod.crawler.*"), user, timestamp)(Path.from("user-agent"))
      .compute(Path.from("""prod.crawler.[host="www.abc.com"].user-agent""")) should be (None)
  }

  it should "return Some(keys) for Namespaces and None for others" in {
    Tree(confValue).traverse(Path.from("*")).flatMap(_.keys) should be (Some(List("key1")))
    Tree(confValue).traverse(Path.from("")).flatMap(_.keys) should be (Some(List("key1")))
    Tree(confValue).traverse(Path.from("key1")).flatMap(_.keys) should be (Some(List("key2", "key3", "key4")))
    Tree(confValue).traverse(Path.from("key1.key2")).flatMap(_.keys) should be (None)
    Tree(confValue).traverse(Path.from("key1.key3")).flatMap(_.keys) should be (None)
    Tree(confValue).traverse(Path.from("key1.*")).flatMap(_.keys) should be (Some(List("key2", "key3", "key4")))
  }

  it should "only store non nullable fields in the tree" in {
    val tree = Tree(Map("key1" -> 1, "key2" -> null, "key3" -> "3", "key4" -> List(1, 2, null, 4)))
    tree should be (Namespace(Map("key1" -> Value(1), "key3" -> Value("3"), "key4" -> ListValue(List(Value(1), Value(2), Value(4))))))
    tree.traverse(Path.from("key2")) should be (None)
  }

  it should "have correct meta info after add" in {
    val initial = Tree(confValue)(nodeMeta1).add(Value(1234)(nodeMeta2), user2, timestamp)(Path.from("key1.key5.key6"))
    initial.traverse(Path.from("key1.key5.key6")).get.getNodeMeta.createdBy should be (user2)
    initial.traverse(Path.from("key1")).get.getNodeMeta.createdBy should be (user1)
    initial.traverse(Path.from("key1")).get.getNodeMeta.lastModifiedBy should be (user2)
    initial.traverse(Path.from("key1.key5")).get.getNodeMeta.createdBy should be (user2)
    initial.traverse(Path.from("key1.key5")).get.getNodeMeta.lastModifiedBy should be (user2)
  }

  it should "have correct meta info after delete" in {
    val initial = Tree(confValue)(nodeMeta1).delete(user2, timestamp)(Path.from("key1.key4"))
    initial.traverse(Path.from("key1.key4")) should be (None)
    initial.traverse(Path.from("key1")).get.getNodeMeta.createdBy should be (user1)
    initial.traverse(Path.from("key1")).get.getNodeMeta.lastModifiedBy should be (user2)
    initial.traverse(Path.from("key1.key2")).get.getNodeMeta.createdBy should be (user1)
    initial.traverse(Path.from("key1.key2")).get.getNodeMeta.lastModifiedBy should be (user1)
  }

  it should "have correct meta info after adding defaults" in {
    val initial = Tree(confValue)(nodeMeta1).addDefault(Path(Nil), user2, timestamp)(Path.from("key1.key5"),Value(1000)(nodeMeta2))
    initial.compute(Path.from("key1.key5")).get.getNodeMeta.createdBy should be (user2)
    initial.compute(Path.from("key1")).get.getNodeMeta.createdBy should be (user1)
    initial.compute(Path.from("key1")).get.getNodeMeta.lastModifiedBy should be (user1)
  }

  it should "have correct meta info after deleting defaults" in {
    val initial = Tree(confValue)(nodeMeta1).addDefault(Path(Nil), user2, timestamp)(Path.from("key1.key5"),Value(1000)(nodeMeta2)).deleteDefault(Path(Nil), user3, timestamp)(Path.from("key1.key5"))
    initial.traverse(Path.from("key1.key5")) should be (None)
    initial.traverse(Path.from("key1")).get.getNodeMeta.createdBy should be (user1)
    initial.traverse(Path.from("key1")).get.getNodeMeta.lastModifiedBy should be (user1)
  }

  it should "traverse reference" in {
    val sourcePath = Path.from("sites.www\\.domain2\\.com.seed")
    val path = Path.from("sites.www\\.domain3\\.com.seed")
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    val res = addLink(root, path, sourcePath)
    root = res._1
    val sourceValue = root.traverse(sourcePath)
    val destValue = root.traverse(path)
    sourceValue.get.asReferences should be (destValue.get.asReferences)
    references.namespace(res._2).asValue should be (destValue.get.asValue)
    references.namespace(res._2) should be (Namespace(Map("type" -> Value("config"), "use-sitemap" -> Value(true))))
  }

  it should "compute reference" in {
    val sourcePath = Path.from("sites.www\\.domain2\\.com.seed")
    val path = Path.from("sites.www\\.domain3\\.com.seed")
    val (root, key) = addLink(Tree(testCrawlSeedConfValue)(nodeMeta1), path, sourcePath)
    val sourceValue = root.compute(sourcePath)
    val destValue = root.compute(path)
    sourceValue.get.asValue should be (Map("type" -> "config", "use-sitemap" -> true))
  }


  it should "edit the cluster when editing reference" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    root = addLink(root, Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))._1
    root.add(Value(false), user, timestamp)(Path.from("sites.www\\.domain3\\.com.seed.use-sitemap"))
      .traverse(Path.from("sites.www\\.domain2\\.com.seed.use-sitemap")) should be (Some(Value(false)))
  }

  it should "add in the cluster when adding values to reference" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    val res = addLink(root, Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))
    root = res._1
    root.add(Namespace(Map("wildcrawl-maxDepth" -> Value(7), "use-wildCrawl" -> Value(true))), user, timestamp)(Path.from("sites.www\\.domain2\\.com.seed.wildCrawl"))
      .traverse(Path.from("sites.www\\.domain3\\.com.seed.wildCrawl")) should be (Some(Namespace(Map("wildcrawl-maxDepth" -> Value(7), "use-wildCrawl" -> Value(true)))))
    references.namespace(res._2).traverse(Path.from("wildCrawl")) should be (Some(Namespace(Map("wildcrawl-maxDepth" -> Value(7), "use-wildCrawl" -> Value(true)))))
  }

  it should "replace references in add" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    val res = addLink(root, Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))
    root = res._1
    root = root.add(Value(10), user, timestamp)(Path.from("sites.www\\.domain2\\.com.seed"))
    root.traverse(Path.from("sites.www\\.domain3\\.com.seed")).get.isReference should be (true)
    root.traverse(Path.from("sites.www\\.domain2\\.com.seed")).get.isReference should be (false)
    root.traverse(Path.from("sites.www\\.domain2\\.com.seed")) should be (Some(Value(10)))
  }

  it should "update the references in patch" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    val res = addLink(root, Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))
    root = res._1
    root = root.patch(Value(10), user, timestamp)(Path.from("sites.www\\.domain2\\.com.seed"))
    root.traverse(Path.from("sites.www\\.domain3\\.com.seed")).get.isReference should be (true)
    root.traverse(Path.from("sites.www\\.domain2\\.com.seed")).get.isReference should be (true)
    root.traverse(Path.from("sites.www\\.domain2\\.com.seed")).map(_.asValue) should be (Some(10))
    root.traverse(Path.from("sites.www\\.domain2\\.com.seed")).map(_.asValue) should be (Some(10))
  }

  it should "delete references" in {
    val (root,key) = addLink(Tree(testCrawlSeedConfValue)(nodeMeta1), Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))
    root.delete(user, timestamp)(Path.from("sites.www\\.domain2\\.com.seed"))
      .exists(Path.from("sites.www\\.domain2\\.com.seed")) should be (false)
  }

  it should "not affect the other references in the cluster when deleting one of the reference " in {
    val (root, key) = addLink(Tree(testCrawlSeedConfValue)(nodeMeta1), Path.from("sites.www\\.domain3\\.com.seed"), Path.from("sites.www\\.domain2\\.com.seed"))
    root.delete(user, timestamp)(Path.from("sites.www\\.domain2\\.com.seed"))
    references.get(key) should be (Namespace(Map("type" -> Value("config"), "use-sitemap" -> Value(true))))
  }

  it should "detect cycles in its subpath" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    root = addLink(root, Path.from("sites.www\\.domain2\\.com.seed.myReference"), Path.from("sites.www\\.domain2\\.com.seed"))._1
    root.cycles() should be (true)
  }

  it should "should not result in false positive cycles" in {
    var root: Node = Namespace(Map("a" -> Namespace(Map("b" -> Namespace(Map.empty)))))(nodeMeta1)
    root = addLink(root, Path.from("a.c"), Path.from("a.b"))._1
    root = root.add(Value(10), user, timestamp)(Path.from("a.c.e"))
    root = addLink(root, Path.from("a.b.f"), Path.from("a.c.e"))._1
    root.cycles() should be (false)
  }

  it should "detect cycles across all paths" in {
    var root = Tree(testCrawlSeedConfValue)(nodeMeta1)
    root = addLink(root, Path.from("sites.www\\.domain3\\.com"), Path.from("sites.www\\.domain2\\.com.seed"))._1
    root.cycles() should be (false)
    root = addLink(root, Path.from("sites.www\\.domain2\\.com.seed.myReference"), Path.from("sites.www\\.domain3\\.com"))._1
    root.cycles() should be (true)
  }

  it should "get/create a reference at a given path" in {
    val refKey = Tree(testCrawlSeedConfValue)(nodeMeta1).extractRef(Path.from("sites.www\\.domain2\\.com.seed"))
    refKey should be (0L)
    references.get(0L) should be (Namespace(Map("type" -> Value("config"), "use-sitemap" -> Value(true))))
  }

  it should "throw ReferenceNotFound exception when reference is not available" in {
    the [RuntimeException] thrownBy {
      Tree(testCrawlSeedConfValue)(nodeMeta1).extractRef(Path.from("sites.www\\.domain3\\.com.seed"))
    } shouldBe a [ReferenceNotFoundException]
  }

}

WASP Configuration as a Service
=

Wasp is a service API which is capable of storing/retrieving/updating configuration in JSON format and handle all the changes along the way. It stores configuration as a Tree of **Nodes**, which allows us to store hierarchical defaults and apply them.

The Structure
-

The configuration tree is made up of 3 types of **Node** s.

**Namespace**

This stores a mapping of a *key* to another *Node*. The root Node is always a Namespace. The JSON maps are represented as Namespaces. It's the only type of Node which can have **Defaults**. A sample JSON structure would be:

    {
        "key1" : value1
    }

**ListValue**

This stores an ordered list of *Node*s. JSON arrays are translated to ListValues in the created configuration tree. JSON looks like:

    [ "value1", "value2", "value3" ]

**Value**

All primitive values are stored as Values. The types included are: Int, Floating decimals, Strings and Boolean values. Sample JSON inputs:

    String  -- "ABCD"
    Int     -- 4132
    Float   -- 15.2543
    Boolean -- true


The Concept of Path
-

Any Node on the configuration can be operated on by the means of providing a *Path*. A Path consists of '.'(Dot) separated components. Each component selects one or more elements from 1 level deeper in the tree. There are 3 types of Path components. The illustrations of Path components are based on the following JSON configuration:

    {
        "production" : {
            "crawler" : {
        		"site-settings" : [
        			{
        				"host" : "www.abc.com",
        				"proxy" : true,
        				"crawl-delay" : 10000,
                        "noOfAgents" : 2
        			},
        			{
        				"host" : "www.xyz.com",
        				"proxy" : false,
        				"crawl-delay" : 3000
        			}
        		]
        	},
        	"parser" : {
        		"www.abc.com" : {
        			"use-javascript" : true,
        			"load-images" : false,
        			"custom-tagger-file" : "abc.js",
        			"tags" : ["Microsoft", "Indix"]
        		},
        		"www.xyz.com" : {
        			"use-javascript" : false,
        			"load-images" : false,
        			"custom-tagger-file" : "xyz.js",
        			"tags": ["Flipkart", "Indix"]
        		}
            }
        }
    }

**For Namespaces**, path components are defined as Strings. So, to get to 'site-settings' of the production's 'crawler', the Path would be:

    production.crawler.site-settings

If the path component has a '.'(Dot) in the name of the key, it needs to be escaped. The Path for parser config for 'www.xyz.com' would be:

    production.parser.www\.xyz\.com

**For ListValues**, we search for a key -> value pair with the syntax: [&lt;key&gt; = &lt;value&gt;]. Say, if we were to find the *crawler's site-settings* for 'www.abc.com', the Path would be:

    production.crawler.site-settings.[host = "www.abc.com"]
    
Also, alternatively, we can search for a particular index in the list if it exists. The index starts from 0(zero) for a list. The syntax is [&lt;index&gt;]. This feature could be very useful in case some editing needs done on a particular *Node* from a UI. The site-setting for 'www.abc.com' can be selected by path:

    production.crawler.site-settings.[0]

NOTE: The search inside [&lt;key&gt; = &lt;value&gt;] is performed on the original configuration without applying defaults first.
This happens since it makes the application of defaults ambiguous.


The values for this search can be string (quoted with "), integers , double (mandatory decimal point), boolean(true or false without quotes).

**With Wildcards** referred to by asterisk('\*'), we match all the Nodes in that level of the tree. Say, we want the *custom-tagger-file* for all the parsers in production. Then, the Path is:

    production.parser.*.custom-tagger-file

**With Selected Keys** with the syntax: {&lt;key1&gt;,  &lt;key2&gt;, ...}, we match all the Nodes which starts with the key in that level of the tree. Say, we want to extract just *use-javascript* and *load-images* for all the parsers in production. Then, the Path is:

    production.parser.*.{use-javascript, load-images}

**With Predicates** with syntax: [&lt;key&gt; &lt;operator&gt; &lt;value&gt;] Currently supported filters include equality, exists(which does not take a value) on namespaces and contains '@' on lists. Say, if we were to find the *parsers's sites* for which 'use-javascript' was true, the Path would be:

    production.parser.[use-javascript = true]

To find the *crawler's sites* for which 'noOfAgents' is defined, the Path would be:

    production.crawler.[noOfAgents ?]

We can search if the List(list) contains a value(value) with the syntax: list @ value. To get *parser's sites* which has the tag 'Microsoft', the Path would be:
    
    production.parser.[tags @ "Microsoft"]
    
The above filters can be combined with boolean operator NOT(!), AND(&), OR(|) with NOT of highest precedence or OR of least. Brackets("()") can be used alternatively to combine them. To get *parser's sites* which has the tag 'Indix' and has 'user-javascript' is true or does not have the tag 'Microsoft', the Path would be: 

    production.parser.[tags @ Indix & (use-javascript = true | ! tags @ "Microsoft")] 


Operations with the Configuration
-

Any operation on the configuration requires a path as a parameter.

Broadly, there are 3 operations we can perform on any *Node* in the configuration tree. To the configuration, we can

+ **Add/update a Node:** We can add/update a *Value*, a *ListValue* or a *Namespace*. This is done using an HTTP POST request providing the namespace and key parameters along with the value of the key Node as the body of the request. An update operation is the same as an add except that it would delete the existing Node and replace it with the supplied Node. Example curl command is:

        curl -H 'Content-Type: text/plain' -XPOST  -d '{"use-javascript" : true, "load-images": true, "custom-tagger-file" : "pqr.js"}' 'http://<wasp-host>:9000/configuration?path=production.parse.www\.pqr\.com'

    Add also allows you to add to all elements in a namespace or list by using wildcards.
    For example, in the given JSON in the previous section, if we'd want to add "user-agent" field to all "site-settings" in "crawler" configuration, it could be done by:

        curl -H 'Content-Type: text/plain' -XPOST  -d '"abcbot"' 'http://<wasp-host>:9000/configuration?path=production.crawler.site-settings.*.user-agent

+ **Delete a Node in it:** An HTTP DELETE request to a namespace and key Node deletes the Node from the configuration. A possible curl command would be:

        curl -H 'Content-Type: text/plain' -XDELETE 'http://<wasp-host>:9000/configuration?path=production.parse.www\.pqr\.com'

    Deleting using wildcards over the specified path removes the node(s) pointed by it. Here, the wildcard matches all keys in a namespace and all elements in a list.
    For instance, if '\*' is the last component and it points to a list, the list is emptied. For example, if we'd want to remove the "user-agent" which we added from all the "site-settings":

        curl -H 'Content-Type: text/plain' -XDELETE 'http://<wasp-host>:9000/configuration?path=production.parse.www\.pqr\.com'


+ **Query for it:** A Node can be queried by simply doing a HTTP GET request for the Path of the Node as below: 

        curl -XGET 'http://<wasp-host>:9000/configuration?path=production.parser.www\.abc\.com'

    The configuration can be queried with paths containing wildcard(s). Wildcard would be replaced by namespaces and list values on searching.
    The request and response, for instance, on searching "production.crawler.site-settings.*.user-agent" would be:

    Request:

        curl -XGET 'http://localhost:9000/configuration?path=production.parser.*.use-javascript'

    Response:

        {
            "www.abc.com":true,
            "www.xyz.com":false
        }

The Concept of Links
-

It is possible to link to an existing configuration on any path in the config tree. On linking both the linked configuration and its parent are moved to an object cluster. 
So any changes to an object in the cluster starts getting reflected to the entire cluster. Also while fetching, the links are resolved to get the entire configuration.

Operations with Links
-

+ **Add Link:** It is done using a HTTP POST request providing the parent path in the body of the request. A link is then created from the given path to the parent path once cyclic references are validated. To create a new parser for www.def.com and to link it to www.abc.com: 
 
		curl -H 'Content-Type: text/plain' -XPOST -d "production.parser.www\.abc\.com" 'http://<wasp-host>:9000/configuration/link?path=production.parser.www\.def\.com'

A referenceId is assigned to the cluster everytime a link gets created.


+ **Updating values inside Link:** Adding values inside links is similar to that of any other node except that it updates the corresponding value of the cluster.
Eg. Updating www.abc.com's 'use-javascript' field to false will change the same 'use-javascript' field of www.def.com's to false and vice versa.

		curl -H 'Content-Type: text/plain' -XPOST -d "false" 'http://<wasp-host>:9000/configuration?path=production.parser.www\.abc\.com.use-javascript'
and

		curl -XGET 'http://<wasp-host>:9000/configuration?path=production.parser.www\.def\.com.use-javascript'

would return false

+ **Unlink:** Any configuration gets unlinked by doing a POST on its/parents's path. 
Eg. To unlink www.abc.com from the cluster, post configuration to its path:

		curl -H 'Content-Type: text/plain' -XPOST  -d '{"use-javascript" : true, "load-images": true, "custom-tagger-file" : "abc2.js"}' 'http://<wasp-host>:9000/configuration?path=production.parse.www\.abc\.com'

would unlink www.abc.com from the cluster. The other configurations in the cluster remain unaffected and continue to remain a reference holding the same ReferenceId.

+ **Update links:** Inorder to update the linked value in the cluster without unlinking, use the HTTP PATCH method. 
The request structure is same as that of the POST method for addition.

Applying the PATCH method on a non reference configuration behaves similar to the POST method.

+ **Get ReferenceId:** The referenceId (if) assigned to a path can be obtained using a GET request to the path.
Eg. To get the referenceId of www.def.com's parser:

		curl -XGET 'http://<wasp-host>:9000/configuration/reference?path=production.parser.www\.def\.com'

If the path is not a reference, 404 status is returned.

+ **Querying cluster with ReferenceId:** The configuration(s) belonging a cluster can be queried by their referenceId.
Say the ReferenceId of the Get ReferenceId returned a number 123, then the cluster can be queried using:

		curl -XGET 'http://<wasp-host>:9000/configuration?path=production.parser.[ # 123]'

Meddling with Defaults
-
Due to the requirements of uniformity amongst objects of same/similar signatures, a user might require pre-filled fields in the objects. Defaults would replace the *Node(s)* while traversing only if the *Node(s)* are empty. Defaults, like *Nodes*, are added/deleted, using POST/DELETE requests, respectively. A *Default* can be any of *Namespace*, *ListValue* or*Value*. The GET request to fetch any *Node* returns the response with the defaults attached to them.

Defaults can be (un)set a different levels and applied when we compute/traverse the path. On traversal, defaults are applied starting from root to the leaf nodes. While adding the defaults, 2 parameters are passed, namely, 

- *atPath* :  The *path* of the element(s), to which defaults are to be added.
- *relativePath* : The *path* relative to that(those) elements where the defaults would be applied.

There are restrictions while applying defaults to Nodes.  Defaults are applied to Nodes in the following manner:

**To Namespaces:** A default can be added to any *Namespace*. Similar to Add(ing) a *Node* but  defaults are separately added to any *Namespace*. To the given example, if a particular value were to be taken as default for "www.abc.com" config, it can be added with any of the given parameters:

- **atPath**: &lt;blank&gt;, **relativePath**: production.parsers.www\.abc\.com.&lt;key&gt;, **POST body**: &lt;value&gt; 
- **atPath**: production, **relativePath**: parsers.www\.abc\.com.&lt;key&gt;, **POST body**: &lt;value&gt; 
- **atPath**: production.parsers.www\.abc\.com, **relativePath**: &lt;key&gt;, **POST body**: &lt;value&gt;

	**Note**: If one were to add defaults to all the *Nodes* inside a *Namespace*, he/she could do that by adding the *Wildcard(\*)* in the atPath or relativePath. The difference here is that 
 
 - when the * is added to atPath, defaults are added to all the *Nodes* traversed using the path AND 
 - when the * is added to the relativePath, the default is added to just the *Node(s)* defined by atPath. 

So, if we later add new *Nodes* to the *atPath*, in the former case, defaults will NOT be added to the new elements and the added in the latter case. Example curl script for addition of default:

		curl -H 'Content-Type: text/plain' -XPOST  -d 'true' 'http://wasp.indix.tv:9000/configuration/default?atPath=production.parser&relativePath=*.use-javascript'

**To Lists:** By nature, *Lists* don't accept defaults. But, if there were *Namespaces* inside these, one could add defaults to them using the *atPath* alone. Also, one can only add defaults to *All or none* of the list elements. There is no middle ground here. 

**To Values:** Defaults can't simply be added to already defined *Values.* This one is obvious.


*That's All Folks!*
-

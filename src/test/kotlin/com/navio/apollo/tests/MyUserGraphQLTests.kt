package com.navio.apollo.tests

import com.graphql.spring.boot.test.GraphQLTestTemplate
import com.navio.apollo.model.MyUser
import org.junit.jupiter.api.Assertions

// Fixtures must be globally unique because they are considered part of our "templates"
// We are reusing the ids 1&2 to show that DB state is per test
// e.g. USER_1 and USER_4 cannot both be inserted within the same test because id is the primary key and they would conflict
val USER_1_MAJOR_TOM = MyUser(1, "Major Tom")
val USER_2_WALTER_WITHERS = MyUser(2, "Walter Withers")
val USER_4_CHEATER_CHEATERSON = MyUser(1, "Cheater Cheaterson")
val USER_5_TOM_PETERSON = MyUser(2, "Tom Peterson")

val CREATE_USER_MUTATION = """
    mutation createUser(${"$"}userId:Int!, ${"$"}name:String!){
      createUser(id:${"$"}userId, name:${"$"}name){
        id
        name
      }
    }"""
    .trimIndent()

val FIND_USER_QUERY = """
    query findUser(${"$"}userId:Int!){
      findUser(id:${"$"}userId){
        name
      }
    }"""
    .trimIndent()

fun findUserVariables(user: MyUser): String {
    return """{"userId": ${user.id} }""".trimIndent()
}

fun createUserVariables(user: MyUser): String {
    return """
            {
              "userId": ${user.id},
              "name": "${user.name}"
            }
        """
        .trimIndent()
}

fun assertFindByIdGraphQL(user: MyUser, graphQLTestTemplate: GraphQLTestTemplate) {
    val findResponse = graphQLTestTemplate.postMultipart(
        FIND_USER_QUERY,
        findUserVariables(user)
    )
    Assertions.assertNotNull(findResponse)
    Assertions.assertTrue(findResponse.isOk)
    Assertions.assertEquals(user.name, findResponse.get("$.data.findUser.name"))
}

fun assertNoSuchUser(user: MyUser, graphQLTestTemplate: GraphQLTestTemplate) {
    val findResponse = graphQLTestTemplate.postMultipart(
        FIND_USER_QUERY,
        findUserVariables(user)
    )
    Assertions.assertNotNull(findResponse)
    Assertions.assertTrue(findResponse.isOk) // Failures are still returned as 200; have to inspect errors
    Assertions.assertTrue(findResponse.get("$.errors[0].message").contains("No value present"))
    // <200,{
    //   "errors" : [ {
    //     "message" : "Exception while fetching data (/findUser) : No value present",
    //     "locations" : [ {
    //       "line" : 2,
    //       "column" : 3
    //     } ],
    //     "path" : [ "findUser" ],
    //     "extensions" : {
    //       "classification" : "DataFetchingException"
    //     }
    //   } ],
    //   "data" : null
    // },[Vary:"Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", Content-Type:"application/json;charset=UTF-8", Content-Length:"296", Date:"Fri, 03 Jan 2020 21:39:46 GMT"]>
}

fun assertCreateUserGraphQL(user: MyUser, graphQLTestTemplate: GraphQLTestTemplate) {
    val createResponse = graphQLTestTemplate.postMultipart(
        CREATE_USER_MUTATION,
        createUserVariables(user)
    )
    Assertions.assertNotNull(createResponse)
    Assertions.assertTrue(createResponse.isOk)
    Assertions.assertEquals(user.id, createResponse.get("$.data.createUser.id", Integer.TYPE))
    Assertions.assertEquals(user.name, createResponse.get("$.data.createUser.name"))
}

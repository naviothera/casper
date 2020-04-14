# Running the Example Application
The example application included in the repo is designed only as a rudimentary scaffold
on which the Casper testing library can be itself tested while maintaining a semblance
of separation between "production" and test code.

**ASSUMPTIONS:** a Postgres DB running on localhost:5432 with a user "apollo" that has create db permission
and an empty password

**NOTE:** It is not actually required to build a new database or migrate schema using flyway unless
you want to _RUN_ the example application -- since the prototype manages creating and configuring all of the
databases that will be used during test you need only have a running postgres with a user that is
allowed to create databases.  The following steps are only necessary if you want to locally run
this prototype or avoid modifying the test/application.properties
 
1) Build the base database, migrate the base schema
```$bash
createdb -h localhost -U apollo zjr
```
2) Build and test; this exercises the core work of this prototype
```$bash
gradle build test
```
3) To verify the prototype service behaves as expected you can also run it if you want:
```$bash
gradle bootRun
```

### Sample GraphiQL queries
```$json
query pingQuery{
  ping
}

query findUser($userId:Int!){
  findUser(id:$userId){
    name
  }
}

query listUsers{
  listUsers{
    id,
    name
  }
}

mutation pingMute{
  ping
}

mutation createUser($userId:Int!, $name:String!){
  createUser(id:$userId, name:$name){
    id
    name
  }
}

mutation updateUser($userId:Int!, $name:String!){
  updateUser(id:$userId, name:$name){
    name
  }
}

mutation deleteUser($userId:Int!){
  deleteUser(id:$userId){
    name
  }
}
```
**GraphQL Variables**
```$json
{
  "name": "Jorge Wunderkind",
  "userId": 1
}
```
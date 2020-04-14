# Casper
Casper is a library for integration testing of Spring Web MVC endpoints (e.g. REST
or GraphQL) that use and modify data stored in Postgres.  Casper provides full test 
data isolation without the use of the `@Transactional` test annotation by creating a 
unique database per test. In this way Casper allows tests to be written
that operate on database state both within the test and the Web MVC test context operating
in a separate Thread **using the same transactional semantics as the production code**.

This avoids several specific issues with tests that depend on transactions in
production code and guarantees no data bleed between tests.  See here for some specifics:
https://www.javacodegeeks.com/2011/12/spring-pitfalls-transactional-tests.html

In order to reduce the overhead of setting up individual per-test databases Casper makes
use of the Postgres template database functionality to set up base database states that
can be reused by multiple tests.  If all of your tests depend on a single fixture state
Casper can set it up once and share fully isolated copies of that state with all of your
tests.  Casper is also capable of providing any number of distinct fixtured templates
as long as the base template is consistent for a given test class.

Casper also makes use of flyway (flywaydb.org) for setting up the base database template
from which all fixtured template databases are derived.

## Why the Name Casper
The name Casper was selected because the isolation should make tests invisible to 
each other (and hopefully the use of the library is itself friendly).

## How to Use Casper
You can bring Casper into a project as a dependency using our maven repository with:
```compile "com.navio.apollo:apollo-framework-casper:1.00.00"```

### Integrating Casper for Testing
The Casper repository contains a working example of a Spring Boot Application with
a simple GraphQL service allowing standard CRUD methods on a basic repository in
the `main` source set.  The Casper library is contained in the `casper` source set
and is the only thing included in the published `casper` artifact.  The `test`
source set contains an example usage of the Casper library for testing the application
defined in the `main` source set.

The easiest way to understand how to use Casper is to look at the examples in the
`test` source set.  The tests are named to indicate their basic intents, such as
loading fixture data only, or loading fixture data and doing some local lookups or
mutations against that data prior to test execution.

### Casper Fixture Data Initialization
To utilize the Casper framework a test MUST extend from `CasperTestBase` and MUST NOT 
use the @Before or @After annotations (see `localSetup` below). Extending `CasperTestBase`
requires one method be implemented:
1) getTestTemplate() : This provides Casper with a callback to invoke to initialize
the base fixture data for this test template if the template has not already
been initialized.  It also is responsible for creating the unique name for
the fixture data set which allows template re-use across tests.

#### localSetup and tearDown (@Before/@After)
Classes that need to do additional setup before or clean up after each test can
do so by overriding the 'localSetup' and 'localTearDown' methods.
* localSetUp(testTemplate: IsolatedTestTemplate): the setup method is invoked
_AFTER_ the test isolated local database has been created from the given template
and is ready to use but _BEFORE_ the test method is called.  At this point a test can 
access entities, fixture data that was part of the template, or otherwise interact 
with the database as normal.  Changes at this point are handled *per test* and not 
shared with other tests or the template.

*NOTE*: tests that override this method _MUST_ ensure the call to the super class
`super.localSetup(testTemplate)`.

* localTearDown() : This method is invoked _AFTER_ the test has completed but
_BEFORE_ the test isolated database is discarded.  Tests do not need to do any
clean-up of state related to the database, this extension point exists only for
other non-persistence related tear down of collaborators or similar.

*NOTE*: tests that override this method _MUST_ ensure the call to the super class
`super.localTearDown()`.

### Casper GraphQL Use
In order to use per-test isolated databases within the test context and the Web
servlet context (which runs in a separate Thread) it is necessary to pass the 
information between the test client and the web service.  Casper does this by
adding a custom Header to the GraphQLTestTemplate and a Filter to the Web MVC Service
to intercept the request, read the custom header, and set the CasperTestContext for 
the Web servlet. This means that calls to a GraphQL endpoint from test that use
Casper should utilize the exposed `graphQLTestTemplate` from the CasperTestBase
class.

### Casper Configuration
Casper depends on Component scanning of the com.navio.apollo.casper package to set up
dependencies for Spring. It also requires that tests declare the CasperApiContextConfig
as a Configuration. Casper also expects to find a set of properties in an `application.properties`
file in the root (typically in the resources) that contains the following properties:

- spring.datasource.driver-class-name=org.postgresql.Driver
- spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:zjr}
- spring.datasource.username=${DB_USERNAME:apollo}
- spring.datasource.password=${DB_PASSWORD:}
- (OPITONAL) spring.datasource.test.template-dbname=${TEMPLATE_DB_NAME:cspr_mytests}
- (OPTIONAL) casper.drop-test-dbs (Boolean, defaults to true)

### Casper Use of ThreadLocal
Casper makes use of ThreadLocal for passing context state between components within the
VM that otherwise operate without direct interaction. This may not work correctly for
RXJava/Continuations/CoRoutines without adaptation. See CasperTestContext.

## Casper Database Clean Up
Casper does not currently delete the template databases that were created while
running tests. On subsequent operations it will drop and recreate templates
since it cannot guarantee the fixture setup is identical but the template
databases will remain in postgres with whatever template naming was defined at
the time the template was created.  This is generally not a problem as templates
are likely small and the Postgres instances used for Casper are intended to be
for testing (and likely subject to tear down and stand-up periodically).

However, if you wish to "clean up" a database that Casper has operated against
you can do so by dropping the template tables it has created.  By default these
will be the name of the database (first 8 characters) followed by "_t" unless
a `spring.datasource.test.template-dbname` was set in the properties.  Once you
know the prefix for the Casper created databases you wish to remove you can
execute the following (adjusting for the correct connection info and uncommenting
the drop call when you are confident you have the right set of tables):

```
for dbname in $(psql -U $USER -h localhost $ROOTDB -c "copy (select datname from pg_database where datname like '$PREFIX%') to stdout") ; do
    echo "$dbname"
    #dropdb -U $USER -h localhost "$dbname"
done
```

## Approach Specifics
Two major hurdles were encountered that turn what would seem like a relatively
simple idea into a somewhat complicated solution:
1) The SpringBoot container manages the DataSource connections which are used
by the underlying JUnit Tests and Hibernate ORM -- in order to make this work
we must create new dynamic DataSources that can be used by those components
as they are encountered
2) The Web MVC servlet running the target services executes in a separate Thread
from the JUnit tests so directly sharing state with ThreadLocal variables
is not possible

The solution to the first issue is to implement a `@Component` that implements
`AbstractRoutingDataSource`; see `CasperRoutingDatasource`.  This allows
us to detect that a DataSource is being requested (via the 
`determineCurrentLookupKey()` method) and respond appropriately.

The solution to the second issue is to send a hint to the Web servlet to
indicate the database to which the request should be targeted.  This is done
by adding a header to the GraphQLTestTemplate on the test side during setUp,
after the initial database is created, and used to initialize the context
via the `CasperDatabaseRoutingFilter` which is wired in via the AutowireCapableBeanFactory
in the `@Configuration class ApiContextConfig`.

Both methods make use of a CasperTestContext that uses a ThreadLocal
val for initializing and passing the context state around.  Note that this
may not work correctly for RXJava/Continuations/CoRoutines without adaptation.

The majority of the actual code needed to manage the database instances takes
place in the CasperDatasourceManager, which is responsible for the creation
of the template databases and the creation and destruction of per-test temporary
databases.

## Steps for running the example application
The example application is not intended for any use aside from the testing of
the Casper library itself.  If you wish to run it instructions are available
in: docs/RUNNING_THE_EXAMPLE_APP.md
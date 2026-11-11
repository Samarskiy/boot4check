# Reproduction app

A minimal Spring Boot 4.0.0 application that demonstrates the silent changes behind rules
B4C-J001, J003, J004, J005 and B002. It is not part of the scanner build.

```
mvn -DskipTests package
java -jar target/verify-0.0.1.jar
```

Output observed on 11 November 2026 (Spring Boot 4.0.0, Java 21):

```
RESULT jackson3 (auto-configured JsonMapper): {"alpha":"a","provider":"Office 365","total":{"cents":1250},"zeta":"z"}
RESULT jackson2 (plain new ObjectMapper):    {"total":"12.50 EUR","provider":"OFFICE_365","zeta":"z","alpha":"a"}
RESULT J001 auto-configured mapper indents output: false
RESULT J004 jackson3 rejected null primitive: MismatchedInputException
RESULT J004 jackson2 accepted null primitives: enabled=false
RESULT B002 flyway tables present (0 = migrations did not run): 0
```

Replacing `flyway-core` with `spring-boot-starter-flyway` in `pom.xml` changes the last line to `2`.

The app calls the auto-configured `JsonMapper` directly; it does not send HTTP requests.

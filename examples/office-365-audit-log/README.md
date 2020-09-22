#  Print the Office 365 Audit log
In this example app, we use the Java SDK to perform various operations:
* Login.
* Get a user's inventory and get a Office 365 company and location. 
* Get and print the Office 365 audit log for the given company.
* Logout.

To run this app, you must fill in your username, password, client-id and client-secret in the file app.properties located in the resources folder.

### Build project
```
mvn clean install
```
### Run example app
```
java -jar target/o365-audit-log-app-1.0.jar
```

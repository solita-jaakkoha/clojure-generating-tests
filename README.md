# api-test-example

This project showcases a method for automatically generating tests -- see
[test_api.clj](test/clj/api_test_example/test_api.clj).

## Running server

```
lein run
```

(in another terminal)

```
$ curl -d "Hello" localhost:3001/api/resource
{:cause :missing-authentication}
$ curl -H "Auth: 1" -H "Content-Type: application/json" -d '{"content": "Hello"}' localhost:3001/api/resource
{:resource-id "4"}
$ curl -H "Auth: 1" localhost:3001/api/resource/4
{:content "Hello"}

$ curl -H "Auth: 2" localhost:3001/api/resource/4
{:cause :not-authorized}
$ curl -H "Auth: 1" -H "Content-Type: application/json" -d '{"resource-id": "4", "token": "2"}' localhost:3001/api/authorization
{}
$ curl -H "Auth: 2" localhost:3001/api/resource/4
{:content "Hello"}
```


## Running tests

```lein test```

# Writing documentation
Documentation should adhere to these rules.
0. If there is something you don't understand about the code, like weird naming, unexpected logic, etc, ask me first so you can document it as well as possible. 
1. All public APIs should have kdocs
2. Private APIs should have kdocs if and only if their function is not self-explanatory from their name, or if they return a value
that has special meaning, or they have a parameter whose purpose is not obvious from the function's name.
3. kdocs should focus on understanding the implementation and explaining hidden information and the relation between components, not just restating the obvious. For example,
This is bad:
\<Bad>
```kotlin
/**
* An interface for objects that can be observed for events of type [T].
*  interface Observable<T>
*/
```
\</Bad>
This is good:
\<Good>
```kotlin
/**
* Allows listening to changes of an object via the `observe` method.
* Usually, another objects owns an [OwnedObservable] implementation of this interface, and emits value to it, which you will receive by calling `observe`.
* In order to preserve memory and avoid additional work, once listening to an observable is no longer required, the `detach` method should be called, as it removes the listener
* from the list of items the Observable needs to manage.
*  interface Observable<T>
*/
```
\</Good>

4. functions/constructors should not use @param. Rather, they should write the usage of the parameters inline using [] notation like is done in the standard library. @return should only be used when returning a value is secondary to the function itself. For example, a getter should not have @return because that's the entirety of what a getter does. However, if a setter returns a value, that's special and should be specially documented with @return.
This is bad:
\<Bad>
```kotlin
/**
* Gets the important part of the bar
* @param bar the bar
* @returns the important part
/*
fun foo(bar: String): Int
```

\</Bad>
This is good:
\<Good>
```kotlin
/**
* Returns the important part of the [bar].
/*
fun foo(bar: String): Int
```
\</Good>
5. For every public API, the "main" part of the API should have a should link to a test method using `@sample` (this is done only once per sample test method), that is an example usage of the API.
   If there is no such test, create one.
   The test should be put in the main source set, under `io.github.natanfudge.fu.test.examples.***` where "***" is the relative package of the thing being documented,
   for example for `io.github.natanfudge.fu.util.Observable.kt`, the sample should be at `io.github.natanfudge.fu.test.examples.util.ObservableExamples.observableExample`
Then for every other public API, `@see` should be used to reference that "main" part of the API that has the `@sample`, where we will see a sample of using that public API. 
If the API is large enough, have multiple sample test methods, and reference them once using `@sample`, and in the same way use `@see` to reference the doc that has the `@sample` that uses that public API. 

6. If there are multiple very similar APIs, focus on the differences between them. 
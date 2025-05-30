# Writing documentation
Documentation should adhere to these rules.
1. All public APIs should have kdocs
2. Private APIs should have kdocs if and only if their function is not self-explanatory from their name, or if they return a value
that has special meaning, or they have a parameter whose purpose is not obvious from the function's name.
3. kdocs should focus on understanding the implementation and explaining hidden information and the relation between components, not just restating the obvious. For example,

Here is an example of good documentation:

```kotlin
/**
* Allows listening to changes of an object via the `observe` method.
* Usually, another objects owns an [OwnedObservable] implementation of this interface, and emits value to it, which you will receive by calling `observe`.
* In order to preserve memory and avoid additional work, once listening to an observable is no longer required, the `detach` method should be called, as it removes the listener
* from the list of items the Observable needs to manage.
*  interface Observable<T>
*/
```


4. functions/constructors should not use @param. Rather, they should write the usage of the parameters inline using [] notation like is done in the standard library. @return should only be used when returning a value is secondary to the function itself. For example, a getter should not have @return because that's the entirety of what a getter does. However, if a setter returns a value, that's special and should be specially documented with @return.

5. Add JUnit tests in the test source set that act as examples for all the public APIs. The process should be as follows:
- A. Group the APIs into functional groups that are used together.
- B. For each group, add a test, using all the APIs in the group. The test should be placed at `io.github.natanfudge.fn.test.examples.<relative-package>.<ApiName>Examples`. Each of these groups should have its own example test.
- C. Identify the "main part"/"entrypoint" of each group, and then add in its documentation `@sample` pointing to the test.
- D. For all other non-main parts of the group, link it to the main part using `@see`.
In general, all APIs should either:
- Have an @sample pointing to a sample
- Have a single @see which points to an API that does have @sample. 


The test should be as simple and concise as possible while still showcasing the entire API. Don't do redundant things like having messages for the assertions. 
Look at other examples to get an idea of how to write the new examples.

6. If there are multiple very similar APIs, focus on the differences between them. 
7. Avoid "AI speak" - i.e. redundant declarations of usefulness, like "this enhances maintainability". You should only mention what the code does and how it should be used. No more, no less. 
8. Avoid adding NEW documentation to `internal` APIs unless you are absolutely sure it's a good idea. 
9. Avoid adding documentation to overriding functions (`override`). 
# Writing documentation 
Documentation should adhere to these rules.
1. All public APIs should have kdocs
2. Private APIs should have kdocs if and only if their function is not self-explanatory from their name, or if they return a value
that has special meaning, or they have a parameter whose purpose is not obvious from the function's name. 
3. kdocs should avoid containing any obvious information.
4. functions/constructors should not use @returns and @param. Rather, they should write the usage of the parameters inline using [] notation like is done in the standard library.  
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
5.
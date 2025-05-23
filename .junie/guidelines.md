# Fun Developer Guidelines

## Project Structure

### Source Code Organization
- **Main Source**: `src/main/kotlin`
  - `io.github.natanfudge.fn` - Core engine packages
    - `hotreload` - Hot reloading functionality
    - `math` - Mathematical utilities
    - `test` - Test examples (see Testing section)
    - `util` - Utility classes
    - `window` - Window management (GLFW, WebGPU)
  - `Main.kt` - Application entry point

### Tech Stack
- **Language**: Kotlin 2.1.x with JVM target
- **Build System**: Gradle with Kotlin DSL
- **Graphics**: LWJGL 3.3.x (GLFW, OpenGL) with plans for Vulkan
- **Async**: Kotlin Coroutines
- **Serialization**: Kotlin Serialization
- **Logging**: Logback
- **Testing**: JUnit 5

## Building and Running

### Prerequisites
- JDK 21 or higher
- Gradle (wrapper included)

### Common Commands
- Build the project: `./gradlew build`
- Run the application: `./gradlew run`
- Run tests: `./gradlew test`

## Testing

### Test Organization
- Tests are stored in two locations:
  1. **Main source set**: `src/main/kotlin/io/github/natanfudge/fn/test/examples`
     - These are example tests that also serve as documentation
  2. **Test source set**: `src/test/kotlin`

### Writing Tests
- Use JUnit 5 with Kotlin Test
- Example tests should be placed in the appropriate package under `io.github.natanfudge.fn.test.examples`
- Write assertions using kotlin-test, and use messages liberally, on every assertion. 

## Documentation

### Code Documentation
- Follow the guidelines in `.junie/DOCUMENTATION.md`
- After finishing writing documentation, review the documentation guideline and fix issues

### Best Practices
- Use hot reloading during development (`FunHotReload.detectHotswap()`)
- Follow Kotlin coding conventions
- Keep functions small and focused
- Use descriptive naming
- Write tests for new functionality
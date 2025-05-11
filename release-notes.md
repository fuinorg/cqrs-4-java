# Release Notes

## 0.7.0
- **Incompatible** Refactored view classes
  - Changed back from `JpaEventHandler` to [EventHandler](core/src/main/java/org/fuin/cqrs4j/core/EventHandler.java).
    This results in changes in several places.
  - Added new [ViewRegistry](core/src/main/java/org/fuin/cqrs4j/core/ViewRegistry.java) that holds information about available views
  - ([View](core/src/main/java/org/fuin/cqrs4j/core/View.java))s now need to implement a `getBeanName()` method to make it more explicit what it is used for.

## 0.6.0
- Added new Jackson module

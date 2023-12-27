data class Book(val name: String, val author: String)

data class BookShelf(val books: MutableList<Book>)

data class Task(val complete: () -> Unit) {
    var isDone: Boolean = false
}

data class ChristmasTree(var toysNumber: Int = 0)

data class Friend(val nickname: String) {
    fun ping(message: String) {
        println("Sent funny sticker to $nickname, '$message'")
    }
}

fun doNewYearCleanUp(
    bookShelf: BookShelf,
    todoList: List<Task>,
    christmasTree: ChristmasTree,
    friends: List<Friend>,
) {
    bookShelf.books.sortWith(compareBy<Book> { it.author }.thenBy { it.name })
    todoList.forEach { task ->
        if (!task.isDone) {
            task.complete()
            task.isDone = true
        }
    }
    christmasTree.toysNumber += 2024
    friends.forEach { friend ->
        friend.ping("Let's make a snowman together")
    }
}

fun main() {
    doNewYearCleanUp(
        BookShelf(
            mutableListOf(
                Book("The Stranger", "Max Frei"),
                Book("Zhalobnaya kniga", "Max Frei"),
                Book("The Razor's Edge", "William Somerset Maugham")
            )
        ),
        listOf(
            Task {
                platform.posix.sleep(1u)
                println("Finally, some sleep...")
            },
            Task {
                listOf("grocery", "clothing", "gifts").forEach { shop ->
                    println("Do shopping at my favourite $shop shop")
                }
            }
        ),
        ChristmasTree(),
        listOf(
            Friend("dear mom"),
            Friend("my love"),
            Friend("coolest bro"),
            Friend("catty the cat")
        )
    )
}

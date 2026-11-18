package com.aistudy.aistudy.game

/** Bundled introductory questions: playable offline without consuming Gemini requests. */
object GameQuestionBank {
    val subjects = listOf("math", "science", "code", "history", "language")
    val questions: List<GameQuestion> = buildList {
        fun question(subject: String, prompt: String, correct: String, b: String, c: String, d: String, explanation: String) {
            add(GameQuestion("$subject-${count { it.subject == subject }}", subject, prompt, listOf(correct, b, c, d), 0, explanation))
        }
        for (n in 2..6) {
            question("math", "$n × ${n + 2} = ?", "${n * (n + 2)}", "${n * (n + 2) + 2}", "${n * (n + 2) - 2}", "${n * (n + 2) + 7}",
                "Multiplication adds equal groups: $n groups of ${n + 2} make ${n * (n + 2)}.")
            question("math", "Solve: x + $n = ${n * 3}", "${n * 2}", "${n * 3}", "$n", "${n * 4}",
                "Subtract $n from both sides: x = ${n * 3} − $n = ${n * 2}.")
        }
        question("science", "Which gas do plants take in during photosynthesis?", "Carbon dioxide", "Oxygen", "Helium", "Hydrogen", "Plants use carbon dioxide and water, with light energy, to make sugars.")
        question("science", "Which organ pumps blood around the body?", "Heart", "Lungs", "Stomach", "Brain", "The heart is a muscle that pumps blood through the circulatory system.")
        question("science", "What is the chemical formula for water?", "H₂O", "CO₂", "O₂", "NaCl", "A water molecule contains two hydrogen atoms and one oxygen atom.")
        question("science", "Which planet is closest to the Sun?", "Mercury", "Venus", "Earth", "Mars", "The first four planets from the Sun are Mercury, Venus, Earth, and Mars.")
        question("science", "Which force pulls objects toward Earth?", "Gravity", "Friction", "Magnetism", "Buoyancy", "Gravity attracts objects with mass toward each other.")
        question("science", "At standard atmospheric pressure, water freezes at…", "0°C", "10°C", "50°C", "100°C", "Pure water freezes at 0°C under standard atmospheric pressure.")
        question("science", "What is the basic unit of life?", "Cell", "Atom", "Organ", "Tissue", "Cells are the smallest structural and functional units of living organisms.")
        question("science", "Which material is usually a good electrical conductor?", "Copper", "Rubber", "Glass", "Dry wood", "Copper has mobile electrons that allow electric current to flow easily.")
        question("science", "Which change turns a liquid into a gas?", "Evaporation", "Freezing", "Condensation", "Melting", "Evaporation changes a liquid into a gas; condensation is the reverse.")
        question("science", "Which object produces its own visible light?", "The Sun", "The Moon", "Earth", "A mirror", "The Sun emits light. The Moon and mirrors reflect light from other sources.")
        question("code", "Which Kotlin keyword declares a read-only variable?", "val", "var", "fun", "class", "val prevents reassignment of the variable. The referenced object may still be mutable.")
        question("code", "In a zero-indexed list, what is the first index?", "0", "1", "−1", "2", "Zero-indexed collections start at index 0.")
        question("code", "Which data type represents true or false?", "Boolean", "String", "Float", "Char", "Boolean values represent logical truth: true or false.")
        question("code", "What does a loop do?", "Repeats instructions", "Only stores text", "Always ends a program", "Only displays an image", "Loops repeat a block of instructions while a condition holds or for a set of items.")
        question("code", "Which structure follows Last In, First Out?", "Stack", "Queue", "Set", "Tree", "A stack removes the most recently added item first (LIFO).")
        question("code", "Which structure follows First In, First Out?", "Queue", "Stack", "Set", "Graph", "A queue removes items in the order they were added (FIFO).")
        question("code", "What is a function parameter?", "An input to a function", "Always a file", "A screen color", "A type of loop", "Parameters name the inputs a function receives when it is called.")
        question("code", "In Kotlin, which operator tests equality?", "==", "=", "!=", ">", "== tests equality; = assigns a value and != tests inequality.")
        question("code", "What is a syntax error?", "A violation of language grammar", "A slow internet connection", "A correct calculation", "A full battery", "Syntax defines how valid code is written. Breaking these rules causes a syntax error.")
        question("code", "How many bits make one byte?", "8", "2", "4", "16", "One byte consists of eight bits; each bit is 0 or 1.")
        question("history", "The ancient pyramids at Giza are in which country?", "Egypt", "Greece", "Italy", "India", "Giza is in Egypt, where pyramids were built as royal tombs.")
        question("history", "The ancient Olympic Games began in…", "Greece", "Egypt", "China", "Mexico", "The ancient Olympic Games were held at Olympia in Greece.")
        question("history", "The Renaissance first developed in which European region?", "Italy", "Scandinavia", "Iceland", "Ireland", "The Renaissance developed in Italian cities before spreading through Europe.")
        question("history", "Which invention is associated with Johannes Gutenberg?", "Movable-type printing in Europe", "The telephone", "The steam engine", "The airplane", "Gutenberg developed a movable-metal-type printing system in fifteenth-century Europe.")
        question("history", "Which civilization built the Colosseum?", "Ancient Rome", "Ancient Egypt", "The Maya", "The Inca", "The Colosseum is a Roman amphitheater in the city of Rome.")
        question("history", "Which trade network connected East Asia with the Mediterranean?", "The Silk Road", "The Panama Canal", "The Suez Canal", "The Trans-Siberian Railway", "The Silk Road was a network of routes used to exchange goods, ideas, and cultures.")
        question("history", "The Industrial Revolution first began in…", "Great Britain", "Brazil", "Australia", "Canada", "The Industrial Revolution began in Great Britain in the eighteenth century.")
        question("history", "Which ancient civilization used hieroglyphic writing along the Nile?", "Ancient Egypt", "Ancient Rome", "The Inca", "The Aztecs", "Egyptian hieroglyphs combined signs representing sounds, words, and meanings.")
        question("history", "The first crewed Moon landing occurred in…", "1969", "1945", "1957", "1989", "Apollo 11 landed on the Moon in July 1969.")
        question("history", "Which event began in France in 1789?", "The French Revolution", "World War I", "The Renaissance", "The Industrial Revolution", "The French Revolution began in 1789 and transformed French government and society.")
        question("language", "Choose the plural of ‘child’.", "Children", "Childs", "Childes", "Childrens", "Child has the irregular plural children.")
        question("language", "Choose the past tense of ‘go’.", "Went", "Goed", "Gone", "Going", "The simple past of go is went; gone is the past participle.")
        question("language", "Which word is a synonym for ‘happy’?", "Glad", "Angry", "Tired", "Afraid", "Glad and happy both describe a feeling of pleasure.")
        question("language", "Which word is the opposite of ‘ancient’?", "Modern", "Old", "Historic", "Early", "Ancient refers to the distant past; modern refers to the present or recent times.")
        question("language", "Complete: She ___ a book every week.", "reads", "read", "reading", "are reading", "In the present simple, a third-person singular subject takes reads.")
        question("language", "Which word is an adjective?", "Beautiful", "Quickly", "Run", "Happiness", "Beautiful describes a noun. Quickly is an adverb; happiness is a noun.")
        question("language", "Choose the correct article: ___ apple.", "An", "A", "These", "Those", "Use an before a singular noun that starts with a vowel sound.")
        question("language", "Which sentence is a question?", "Where do you live?", "I live here.", "Please sit down.", "What a day!", "Where do you live? asks for information and ends with a question mark.")
        question("language", "Complete: They ___ playing football now.", "are", "is", "am", "be", "They takes are; are playing is the present continuous form.")
        question("language", "Which word is a noun?", "Teacher", "Teach", "Quickly", "Beautiful", "Teacher names a person and is a noun. Teach is a verb.")
    }
}

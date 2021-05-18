package com.example.netologyvoiceassistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.opengl.Visibility
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    lateinit var requestInput: TextView  //будет хранить содержимое поля ввода текста
    lateinit var waEngine: WAEngine  //будет хранить ссылку на движок Вольфрама из его библиотеки
    val searches = mutableListOf<HashMap<String, String>>()  //будет хранить список из HashMap(String вопрос, String ответ)
    lateinit var searchesAdapter: SimpleAdapter  //адаптер предоставляет данные внутрь виджета со списком (списком запросов к Вольфраму)
    lateinit var textToSpeech: TextToSpeech  //TextToSpeech - класс, отвечающий за преобразование текста в речь
    lateinit var stopButton: FloatingActionButton //кнопка остановки воспроизведения озвучки ответа вольфрама
    val TTS_REQUEST_CODE = 1  //ключ запроса (цифра м.б. любой, нужна просто любая константа). TTS - text to speach,

    override fun onCreate(savedInstanceState: Bundle?) {   //во время создания экрана (запуска приложения)
        Log.d("netology voice", "start of onCreate function")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  //применяется XML-файл с разметкой

        setSupportActionBar(findViewById(R.id.topAppBar))  //создается верхняя панель
        initVews()     //инициализация виджетов
        initWolframEngine()  //начало работы библиотеки/движка Вольфрам
        initTts()  //начало работы метода распознавания речи

        Log.d("netology voice", "end of onCreate function")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {  //переопределяем этот метод, чтобы меню отображалось на экране. Код метода дефолтный
        menuInflater.inflate(R.menu.toolbar_menu, menu)  //просто указываем название нашего меню
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {  //переопределяем этот метод, чтобы указать, что происходит при нажатии на кнопки меню
        when (item.itemId) {

            R.id.action_search -> {   //Если пользователь нажал на кнопку меню "запрос Вольфраму"...
                val question = requestInput.text.toString() //текст из  поля ввода текста (requestInput) передаем в текстовую переменную question
                askWolfram(question)   //передаем введенный текст в метод askWolfram
                return true  //эта строчка нужна просто потому что метод должен вернуть boolean
            }


            R.id.action_voice -> {  //Если пользователь нажал на кнопку меню "распознавание речи"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)  //Intent - класс Android, широко применяется при обращении к чему-либо (к камере, контактам, в данном случае - к распознавателю речи)
                intent.putExtra(  //intent  необходимо расширить (метод putExtra), чтобы указать доп. параметры
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM   //указываем тип запроса - свободный (FREE_FORM) (помимо него есть стандартизированные)
                )
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to know?")  //подсказка для пользователя в тот момент, когда он будет начитывать вопрос
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)  //указываем язык голосовых запросов
                try {  //после создания intent'a необходимо его выполнить
                    startActivityForResult(intent, TTS_REQUEST_CODE)  // метод "startActivityForResult" выполняет созданный выше intent --> из нашего приложения переключаемся в другое activity (распознаватель речи)
                } catch (a: ActivityNotFoundException) {  //если на смартфоне нет приложения распознавания речи - выводим сообщение об ошибке
                    Toast.makeText(applicationContext, "TTS not found", Toast.LENGTH_SHORT).show()  //Toast.LENGTH_SHORT - время вывода сообщения об ошибке (SHORT - около 1 сек, LONG - 3 сек)
                }
                return true //эта строчка нужна просто потому что метод должен вернуть boolean
            }
            else -> return super.onOptionsItemSelected(item)  //эта строчка нужна просто потому что метод должен вернуть boolean
        }
    }


    private fun initVews() {  //инициализируем переменые, связанные с виджетами
        requestInput = findViewById<TextView>(R.id.request_input)  //связывваем переменную requestInput с XML-виджетом request_input (поле ввода текста)
        val searchesList = findViewById<ListView>(R.id.searches_list)  //привязываем переменную searchesList к XML-виджету со списком запросов к Вольфраму

        searchesAdapter = SimpleAdapter(   //создаем адаптер к списку. При этом мы должны заполнить все его параметры конструктора (внутри круглых скобкох ниже)
            applicationContext,  //1-й параметр конструктора SimpleAdapter: контекст ашего приложения
            searches,   //2-й параметр - тот список, к которому мы создаем адаптер
            R.layout.item_search, //XML-разметка как будет выглядеть 1 элемент списка
            arrayOf("Request", "Response"), //из списка HashMap'ов "searches" (2-й аргумент конструктора) берем очередную HashMap'у, содержащую ключ (Request) и значение (Response)
            intArrayOf(R.id.request, R.id.response) //кладем в созданные нами одноименные XML элементы  item_search (выбран нами на 2 строки выше)
                //таким образом, адаптер searchesAdapter связывает список "searches", состоящий из HashMap'ов (request - response) с XML-элементом item_search
        )

        searchesList.adapter = searchesAdapter   //после создания адаптера надо его применить

        searchesList.setOnItemClickListener { parent, view, position, id ->   //для переменной виджета списка searchesList добавим слушателя нажатия. Также учитываем какой именно эл-т списка нажали (position)
                    // (чтобы при нажатии на любой из прошлых ответов Вольфрама он повторно воспроизводился голосом). Список прошлых запросов/ответов хранится в переменной searchesList
            val request = searches[position] ["Request"] //по позиции "position" из списка searches (состоящего из HashMap'ов с ключом "Request" и значением "Response")  получаем "Request"
            val response = searches[position] ["Response"]   // из списка ответов "searches" будем брать вопрос "request" и ответ "response" по позиции "position"
            textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, request) // Ответ будем воспроизводить с помощью метода "speak", а вопрос (request) используем в качестве идентификатора.
        }

        stopButton = findViewById<FloatingActionButton>(R.id.stop_button) //проинициализируем переменную stopButton, свзязав ее с кнопкой stop_button из XML

        stopButton.setOnClickListener {   //метод "setOnClickListener" у кнопки позволяет задать логику при нажатии на нее (в данном случае - остановка воспроизведения озвучки ответа Вольфрама)
            textToSpeech.stop()
            stopButton.visibility = View.GONE  //когда воспроизведение остановлено, кнопка временно становится невидимой
        }

    }


    fun initWolframEngine() {  //создаем движок Вольфрам (из подключенной/импортитрованной внешней библиотеки Вольфрам)
        waEngine = WAEngine()  //присвоили переменной ссылку на объект движка Вольфрам (из подключенной/импортированной внешней библиотеки Вольфрам)
        waEngine.appID = "DEMO"  //ключ, id
        waEngine.addFormat("plaintext")
    }


    fun askWolfram(request: String) {  //метод, который делает запросы к Вольфраму
        Toast.makeText(applicationContext, "Let me think...", Toast.LENGTH_SHORT).show()   //вспомогательная всплывающая подсказка, показывающая, что запроос к Вольфраму происходит
        CoroutineScope(Dispatchers.IO).launch {  //находящийся ниже блок кода будет выполняться в ПОБОЧНОМ ПОТОКЕ "IO"
            val query = waEngine.createQuery().apply {
                input = request }   //query - запрос, который делаем из движка WAENgine
            val queryResult = waEngine.performQuery(query)  //queryResult - результат запроса к Вольфраму (т.е. в этой переменной будет содержаться ОТВЕТ)
                        //  performQuery - исполнить запрос (метод из библиотеки Вольфрам)
            //в нижеуказанном коде из ответа, полученного от Вольфрама, получаем СТРОКУ
            val response = if (queryResult.isError) {  //в переменную "response" занесется результат нижекуказанного блока if-else
                queryResult.errorMessage //если в холе запроса возникла ошибка - выведем сообщение об ошибке
            } else if (!queryResult.isSuccess) {  //если запрос не прошел, но без ошибки, выведем сообщение "Sorry..." (например, если запрос на русском)
                "Sorry, I don't understand. Can you rephrase?"
            } else {   // если ошибки нет, преобразовываем ответ Вольфрама в строку (str) - для этого идут все оставшияся условия
                val str = StringBuilder()
                //оставшаяся часть условий - код из документации Вольфрама. Нужен для преобразования ответа Вольфрама из формата JSON к формату String
                for (pod in queryResult.pods) {
                    if (!pod.isError) {
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    str.append(element.text)
                                }
                            }
                        }
                    }
                }
                str.toString()
            }

            withContext(Dispatchers.Main) {  //ответ Вольфрама, полученный выше (в побочном потоке) (и сохраненный в переменную "response") нужно передать в ГЛАВНЫЙ ПОТОК (Main) --> переключаемся на него
                searches.add(0, HashMap<String, String>().apply { //добавляем request, response в список  вопросов-ответов "searches", состоящий из HashMap
                    // используем нулевой индекс, чтобы каждый новый ответ устанавливался в начало списка
                    put("Request", request)
                    put("Response", response)
                })
                searchesAdapter.notifyDataSetChanged()  //после добавления нового элемента в спискок вопросов-ответов, ПЕРЕРИСОВЫВАЕМ его - для этого исп. метод notifyDataSetChanged
                textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, request) //голосовое воспроизведение "response" (текст ответа от сервера Вольфрама)
                        //QUEUE_FLUSH ("очистка очереди") - константа, которая означает, что если что-то уже воспроизводится в тот момент, когда нужно воспроизвести новый текст, то первое воспроизв-е прекращается
            }
        }
    }

    fun initTts() {   //инициализируем переменную класса TextToSpeech, отвечающего за преобразование текста в голос
        textToSpeech = TextToSpeech(this) {   code ->  //создадим экземпляр класса TextToSpeech, в конструкторе нужно указать контекст
            if (code == TextToSpeech.SUCCESS) {   //вспомогательный условный оператор, проверяющий, работает ли на смартфоне функция TextToSpeech (проинициализровалась переменная textToSpeech, или нет)
                Log.d("MainActivity", "TextToSpeech.SUCCESS")
            } else {
                Log.e("MainActivity", "Error: $code")
            }
        }
        textToSpeech.language = Locale.US    //установим язык, который использует TextToSpeech
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() { //добавим слушатель событий для переменной textToSpeech (нужен для того, чтобы отображать кнопку паузы только пока что-то воспроизводится)
            override fun onStart(utteranceId: String?) {  //добавим слушатель событий СТАРТА воспроизведения
                stopButton.post {stopButton.visibility = View.VISIBLE}  //При старте воспроизведения будем отображать кнопку остановки воспроизведения
            }

            override fun onDone(utteranceId: String?) { //добавим слушатель событий  окончания воспроизведения,
                stopButton.post {stopButton.visibility = View.GONE}  //При окончании воспроизведения будем прятать кнопку остановки воспроизведения
            }

            override fun onError(utteranceId: String?) {  //добавим слушатель событий ошибки воспроизведения

            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    //override fun onAcivityResult(requestCode: Int, resultCode: Int, data: Intent?) { //получение результата работы приложения, озвучивающего текстовый ответ Вольфрама
        // "?" - "safe call" оператор проверки на null. Означает, что данных может не быть. К нему можно обращаться, не делая проверку на null. (В Java пришлось бы каждый раз делать проверку if(... != null) {...} )
        super.onActivityResult(requestCode, resultCode, data) //сначала вызываем родит. версию метода. data - это результат работы приложения (голос из текста или наоборот)
        //в пришедшем ответе надо проверить 2 вещи: (1) тот ли ответ пришел, который мы ожидали (сравниваем с ключом TTS_REQUEST_CODE)
        // (2) что запрос исполнился корректно. Для этого исп. константу RESULT_OK
        if (requestCode == TTS_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let { question ->  //из данных, полученных от распознавателя речи, извлекаем массив строчек. Нас интересует нулевая строка
                requestInput.text = question //передаем в поле ввода текста (requestInput) текстовый запрос (question), полученный в рез-те работы распознавателя речи
                askWolfram(question)  //передаем в Вольфрам текстовый запрос (question), полученный в рез-те работы распознавателя речи
            }
        }
    }


}
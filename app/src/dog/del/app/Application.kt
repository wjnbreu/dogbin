package dog.del.app

import com.mitchellbosecke.pebble.loader.ClasspathLoader
import dog.del.app.frontend.frontend
import dog.del.app.frontend.legacyApi
import dog.del.app.session.ApiSession
import dog.del.app.session.WebSession
import dog.del.app.session.XdSessionStorage
import dog.del.app.utils.DogbinPebbleExtension
import dog.del.commons.keygen.KeyGenerator
import dog.del.commons.keygen.PhoneticKeyGenerator
import dog.del.data.base.Database
import dog.del.data.base.model.config.Config
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.gson.*
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.pebble.Pebble
import io.ktor.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.header
import io.ktor.util.hex
import jetbrains.exodus.database.TransientEntityStore
import ktor_health_check.Health
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import java.io.File

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(Koin) {
        val appModule = org.koin.dsl.module {
            // TODO: introduce config system
            single { Database.init(File("dev.xdb"), "dev") }
            single { Config.getConfig(get()) }
            single<KeyGenerator> { PhoneticKeyGenerator() }
        }
        modules(
            appModule
        )
    }

    install(ContentNegotiation) {
        gson {
        }
    }

    install(Health) {
        healthCheck("running") { true }
        healthCheck("database") {
            val db = get<TransientEntityStore>()
            db.isOpen
        }
    }

    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
            suffix = ".peb"
        })
        extension(DogbinPebbleExtension())
    }

    install(Sessions) {
        // TODO: move these to config and make them more secure
        val key = hex("DEADBEEF")
        cookie<WebSession>("doggie_session", XdSessionStorage()) {
            transform(SessionTransportTransformerMessageAuthentication(key))
        }
        header<ApiSession>("session", XdSessionStorage()) {
            transform(SessionTransportTransformerMessageAuthentication(key))
        }
    }

    routing {
        static("static") {
            resources("static")
        }
        static {
            resource("favicon.ico", resourcePackage = "static")
        }
        legacyApi()
        frontend()
    }
}

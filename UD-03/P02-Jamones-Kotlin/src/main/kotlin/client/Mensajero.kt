package client

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.Jamon
import models.Lote
import models.Request
import models.Response
import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

private val logger = KotlinLogging.logger {}
private val json = Json { prettyPrint = true }

private const val HOST = "localhost"
private const val PORT = 6969

/**
 * Dos filosofías, si queremos podemos estar en un bucle while y recibir/mandar
 * mensajes hasta que el cliente se desconecte, o bien, (como haría FTP)
 * O atender y desconectar (desconexion) en cada petición para liberar recursos (como hace http)
 * La diferencia es que en el primer caso, el servidor tiene que estar preparado para recibir
 * peticiones de varios clientes a la vez que se liberan, y en el segundo caso, el servidor tiene que estar
 * preparado para recibir peticiones de varios clientes pero no se liberan hasta que el cliente se desconecta
 * y esta desconexión es la que libera los recursos y puede tardar!!!
 */


class Mensajero(val id: String, val tamLote: Int = 3, val maxLotes: Int = 20, val pausa: Int = 3000) : Runnable {
    override fun run() {
        val misJamones = mutableListOf<Jamon>()
        var numLote = 1
        while (numLote <= maxLotes) {
            try {
                // Creamos el socket
                val socket = Socket(InetAddress.getByName(HOST), PORT)
                // Para poder leer y escribir en el socket
                // Para leer String, si no sería ObjectInputStream
                val entrada = DataInputStream(socket.inputStream)

                // Para escribir String si no sería ObjectOutputStream
                val salida = DataOutputStream(socket.outputStream)

                // Enviamos una petición al servidor
                val request = Request<Jamon>(
                    content = null,
                    type = Request.Type.GET
                )
                val peticion = json.encodeToString(request)
                logger.debug { "Petición: $peticion" }
                salida.writeUTF(peticion)

                // Leemos la respuesta del servidor
                val mensaje = entrada.readUTF()
                // Sacamos la respuesta del mensaje y la convertimos a objeto
                val respuesta = json.decodeFromString<Response<Jamon>>(mensaje)
                logger.debug { "Respuesta recibida: $respuesta" }
                if (respuesta.type == Response.Type.OK) {
                    println("$id: jamon recibido -> ${respuesta.content}")
                    respuesta.content?.let { jamon -> misJamones.add(jamon) }

                    // Si puedo sacar un lote lo imprimo
                    if (misJamones.size % tamLote == 0) {
                        val lote = Lote(idMensajero = id, jamones = misJamones)
                        val loteJson = Json.encodeToString(lote)
                        println("$id: lote ${numLote} creado -> $loteJson")
                        misJamones.clear()
                        numLote++
                    }
                } else {
                    logger.error { "Error al enviar jamón a secadero" }
                }

                // Cerramos todo
                entrada.close()
                salida.close()
                socket.close()

                // Esperamos un poco
                Thread.sleep(pausa.toLong())
            } catch (ex: IOException) {
                logger.error { "Error al atender al cliente: ${ex.message}" }
                // return of run()
            }
        }
        println("Mensajero $id finalizado")
    }
}
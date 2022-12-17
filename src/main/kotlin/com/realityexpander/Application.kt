package com.realityexpander

// Course Idea: https://elopage.com/payer/s/philipplackner/courses/doodlekong?course_session_id=3501878&lesson_id=1055226

// Ktor generator
// https://start.ktor.io/#/final?name=ktor-drawing-server&website=realityexpander.com&artifact=com.realityexpander.ktor-drawing-server&kotlinVersion=1.7.0&ktorVersion=1.5.3&buildSystem=GRADLE&engine=NETTY&configurationIn=HOCON&addSampleCode=true&plugins=content-negotiation%2Crouting%2Cktor-gson%2Cktor-websockets%2Cshutdown-url%2Ccall-logging

////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// DEPLOY TO HEROKU STEPS
// https://devcenter.heroku.com/articles/deploying-gradle-apps-on-heroku
//
// make a heroku account:
//   https://dashboard.heroku.com/signup
//
// Dashboard:
//   https://dashboard.heroku.com/apps/guess-a-sketch-server
//
// Add the following to your project:
//   app.json   # this is the file that heroku uses to deploy your app
//   .env       # environment variables
//   Procfile   # tells heroku how to run your app (just a shell command) NOTE: be sure to update the version of your app!
//
// Heroku CLI:
//   https://devcenter.heroku.com/articles/heroku-cli#install-the-heroku-cli
//
// Login to heroku from the command line:
//   heroku login
//
// create a new app via heroku cli:
//   heroku create
//
// Rename the app:
//   heroku rename <new-app-name>
//
// Add the following to your .env file:
//   PORT=8005
//   DATABASE_URL=postgres://<user>:<password>@<host>:<port>/<database>  #not used but kept for refernece
//   SECRET=<secret>
//
// Configure gradle for heroku:
//   heroku config:set GRADLE_TASK="build -x test"       # build without(-x) tests
//
// Set Environment Variables (instead of using .env file, also accessible from the heroku dashboard):
// https://dashboard.heroku.com/apps/guess-a-sketch-server/settings
//   heroku config:add PORT=8005  # this is the port that heroku will use to run your app
//
// In Android Studio, Update the `./data/remote/common.Constants.kt` file:
//     USE_LOCALHOST=false
//     REMOTE_HOST_TYPE="HEROKU"
//     HTTP_BASE_URL_REMOTE_HEROKU = "https://<HEROKU_APP_NAME>.herokuapp.com:8005"
//     WS_BASE_URL_REMOTE_HEROKU = "ws://<HEROKU_APP_NAME>.herokuapp.com:8005/ws/draw"

// To deploy current version to heroku from master branch:
//   git push heroku master:main
// or to deploy a particular branch:
//   git push heroku <branch-name>:main

// Check logs from heroku (run locally):
//   heroku logs --tail

////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// DEPLOY TO UBUNTU STEPS
//
// Deployment step by step:
//
// 1. Download Git Bash (only if on Windows)
//
// 2. With terminal, cd into the `~/.ssh` folder.
//    Generate a key pair:
//   ssh-keygen -m PEM -t rsa -b 2048
//    Give the key a name, this is your <keyname>
//
// 3. Copy the PRIVATE key to your server:
//   ssh-copy-id -i <keyname> root@<host>
//    Enter password for the server.
//
// 4. In your IntelliJ project, create a folder called `keys` in the root folder,
//    Add the `/keys` folder to your `.gitignore` file.
//    Copy the private key <keyname> from the `~/.ssh` into this `keys` folder.
//    NOTE: This is currently only used for the `ant.scp` and `ant.ssh` tasks.
//
// 5. Login to your Ubuntu server via SSH private key:
//   ssh -i <keyname> root@<host>
//
//    To SSH into the server without long reference to `~/.ssh/hostinger_rsa`, do the following:
//     nano ~/.ssh/config  # & Add these lines: (to allow ssh/scp/sftp to work without supplying password)
//
//     Host <shortcut-name>
//       Host <server-ip-address>
//       User root
//       IdentityFile ~/.ssh/<private_keyname>
//
//     `^s to save`
//     `^x to exit`
//    Now you can SSH into the server without long reference to `~/.ssh/<keyname>`:
//     ssh <shortcut-name>
//
// 6. Update dependencies:
//   sudo apt update
//
// 7. Install Java:
//   sudo apt-get install default-jdk
//
// 8. Open /etc/ssh/sshd_config:
//   sudo nano /etc/ssh/sshd_config
//
// 9. Put this key exchange algorithm configuration string in there, save with Ctrl+S and exit with Ctrl+X:
//   KexAlgorithms curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1
//
// 10. Restart the sshd service to apply changes in the config file `/etc/ssh/sshd_config`:
//   sudo systemctl restart sshd
//
// 11. Create a systemd service for your Ktor server:
//   sudo nano /etc/systemd/system/guessasketch.service
//
// 12. Paste this configuration in this service, then save with Ctrl+S and exit with Ctrl+X:
//   [Unit]
//   Description=Guess-a-Sketch server app Service
//   After=network.target
//   StartLimitIntervalSec=10
//   StartLimitBurst=5
//
//   [Service]
//   Type=simple
//   Restart=always
//   RestartSec=1
//   User=root
//   ExecStart=/usr/bin/java -jar /root/guessasketch/ktor-guessasketch-server.jar
//
//   [Install]
//   WantedBy=multi-user.target
//
// 13. Launch the service:
//   sudo systemctl start guessasketch
//
// 14. Create a symlink to automatically launch the service on boot up:
//   sudo systemctl enable guessasketch
//
// 15. Make sure, your ports are open and you forward the traffic from the standard HTTP port to 8005:
//   iptables -t nat -I PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8005  # routes 80 to 8005
//   sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT   # opens port 80 for incoming connections
//   sudo iptables -A INPUT -p tcp --dport 8005 -j ACCEPT # opens port 8005 for incoming connections
//
// 16. Then, save your iptables rules to reload at bootup:
//   sudo apt-get install iptables-persistent
//   <enter>
//   <enter>
//
// 17. Create the folder structure
//   cd /root
//   mkdir guessasketch
//   cd guessasketch
//   mkdir resources
//
// 18. Update the `.env` file with the following:
//   PORT=8005
//   PRIVATE_KEY_FILENAME=<PRIVATE_KEY_FILENAME>       # the name of the private key file in the `keys` folder
//   HOST_SERVER_IP_ADDRESS=<HOST_SERVER_IP_ADDRESS>   # the IP address of the server you are connecting to
//   HOST_SERVER_USERNAME=<HOST_SERVER_USERNAME>       # usually root
//
// 19. In Android Studio, Update the `./data/remote/common.Constants.kt` file:
//     USE_LOCALHOST=false
//     REMOTE_HOST_TYPE="UBUNTU"
//     HTTP_BASE_URL_REMOTE_UBUNTU = "http://<HOST_SERVER_IP_ADDRESS>:8005"
//     WS_BASE_URL_REMOTE_UBUNTU = "ws://<HOST_SERVER_IP_ADDRESS>:8005/ws/draw"
//
// 20. Run the `deployToUbuntu` task in IntelliJ

// To Check if app is running (run on server):
//   ps aux | grep java

// Check the running logs (run on server):
//   journalctl -u guessasketch.service

// Check the service logs (run on server):
//   systemctl status guessasketch



import com.google.gson.Gson
import com.realityexpander.common.Constants.QUERY_PARAMETER_CLIENT_ID
import io.ktor.application.*
import com.realityexpander.plugins.*
import com.realityexpander.routes.createRoomRoute
import com.realityexpander.routes.gameWebSocketRoute
import com.realityexpander.routes.getRoomsRoute
import com.realityexpander.routes.joinRoomRoute
import com.realityexpander.session.DrawingSession
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import org.slf4j.event.Level

// Globals
val serverDB = SketchServer() // represent the database
val gson = Gson()

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {

    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }

    // Set up the sessions & get the clientId from the query parameter
    intercept(ApplicationCallPipeline.Features) {
        call.sessions.get<DrawingSession>() ?: run {

            // Get the clientId from the client query parameter
            val clientId = call.parameters[QUERY_PARAMETER_CLIENT_ID] ?:  throw IllegalArgumentException("clientId is required")

            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }

    install(WebSockets)

    configureRouting()

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    // Setup the GSON serializer
    configureSerialization()

    // Setup the shutdown url
    configureAdministration()
}

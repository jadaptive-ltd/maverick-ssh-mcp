pipeline {
    agent none

    tools {
        maven 'Maven 3.9.0'
        jdk 'Graal JDK 25'
    }

    stages {
        stage('Maverick SSH MCP') {
            parallel {
                stage('Linux x86_64 Maverick SSH MCP Native') {
                    agent {
                        label 'linux && x86_64'
                    }
                    steps {
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULL_VERSION}"
                        }

                        configFileProvider([
                            configFile(
                                fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                                replaceTokens: true,
                                targetLocation: 'jadaptive.build.properties',
                                variable: 'BUILD_PROPERTIES'
                            )
                        ]) {
                            withMaven(
                                globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                            ) {
                                sh '''
                                awk -F= '$1 != "gpg.keyname" { print }' "$BUILD_PROPERTIES" > "$WORKSPACE/jadaptive.build.nosign.properties"
                                mvn -U -P native-image,linux-packages \
                                    -Dbuild.projectProperties="$WORKSPACE/jadaptive.build.nosign.properties" \
                                    -Dathene.api=https://athene.jadaptive.com \
                                    -Dathene.serverId=athene \
                                    -Dathene.repo=jadaptive \
                                    -Dbuild.number="${BUILD_NUMBER}" \
                                    clean deploy
                                '''
                            }
                        }

                        sh '''
                        mkdir -p docker/native
                        cp target/maverick-ssh-mcp docker/native/maverick-ssh-mcp-linux-amd64
                        '''
                        stash name: 'maverick-ssh-mcp-native-linux-amd64', includes: 'docker/native/maverick-ssh-mcp-linux-amd64'

                      
                    }
                }

                stage('Linux aarch64 Maverick SSH MCP Native') {
                    agent {
                        label 'linux && aarch64'
                    }
                    steps {
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULL_VERSION}"
                        }

                        configFileProvider([
                            configFile(
                                fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                                replaceTokens: true,
                                targetLocation: 'jadaptive.build.properties',
                                variable: 'BUILD_PROPERTIES'
                            )
                        ]) {
                            withMaven(
                                globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                            ) {
                               sh '''
                                awk -F= '$1 != "gpg.keyname" { print }' "$BUILD_PROPERTIES" > "$WORKSPACE/jadaptive.build.nosign.properties"
                                mvn -U -P native-image,linux-packages \
                                    -Dbuild.projectProperties="$WORKSPACE/jadaptive.build.nosign.properties" \
                                    -Dathene.api=https://athene.jadaptive.com \
                                    -Dathene.serverId=athene \
                                    -Dathene.repo=jadaptive \
                                    -Dbuild.number="${BUILD_NUMBER}" \
                                    clean deploy
                                '''
                            }
                        }

                        sh '''
                        mkdir -p docker/native
                        cp target/maverick-ssh-mcp docker/native/maverick-ssh-mcp-linux-arm64
                        '''
                        stash name: 'maverick-ssh-mcp-native-linux-arm64', includes: 'docker/native/maverick-ssh-mcp-linux-arm64'
                    }
                }

                stage('macOS x86_64 Maverick SSH MCP Native') {
                    agent {
                        label 'macos && x86_64'
                    }
                    steps {
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULL_VERSION}"
                        }

                        configFileProvider([
                            configFile(
                                fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                                replaceTokens: true,
                                targetLocation: 'jadaptive.build.properties',
                                variable: 'BUILD_PROPERTIES'
                            )
                        ]) {
                            withCredentials([string(credentialsId: 'macos-signing-certificate-passphrase', variable: 'SIGNING_PASSPHRASE')]) {
                                withMaven(
                                    globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                                ) {
                                    
                                    sh '''
                                        mvn -U -P native-image,macos-packages,amd64-packages \
                                        "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
                                        -Dathene.api=https://athene.jadaptive.com \
                                        -Dathene.repo=jadaptive \
                                        -Dathene.serverId=athene \
                                        -Dathene.macos.sign.key=apple-codesign/222d3c8e269a4e5f98e3c9575aa8e810 \
                                        "-Dathene.macos.sign.passphrase=$SIGNING_PASSPHRASE" \
                                        -Dbuild.number="${BUILD_NUMBER}" \
                                        clean deploy
                                    '''

                                    sh '''
                                    mkdir -p mcpb/server
                                    cp target/maverick-ssh-mcp mcpb/server/maverick-ssh-mcp-macos-amd64
                                    '''
                                    stash name: 'maverick-ssh-mcp-native-macos-amd64', includes: 'mcpb/server/maverick-ssh-mcp-macos-amd64'
                                }
                            }
                        }
                    }
                }

                stage('macOS aarch64 Maverick SSH MCP Native') {
                    agent {
                        label 'macos && aarch64'
                    }
                    steps {
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULL_VERSION}"
                        }

                        configFileProvider([
                            configFile(
                                fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                                replaceTokens: true,
                                targetLocation: 'jadaptive.build.properties',
                                variable: 'BUILD_PROPERTIES'
                            )
                        ]) {
                            withCredentials([string(credentialsId: 'macos-signing-certificate-passphrase', variable: 'SIGNING_PASSPHRASE')]) {
                                withMaven(
                                    globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                                ) {
                                    sh '''
                                        mvn -U -P native-image,macos-packages,aarch64-packages \
                                        "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
                                        -Dathene.api=https://athene.jadaptive.com \
                                        -Dathene.repo=jadaptive \
                                        -Dathene.serverId=athene \
                                        -Dathene.macos.sign.key=apple-codesign/222d3c8e269a4e5f98e3c9575aa8e810 \
                                        "-Dathene.macos.sign.passphrase=$SIGNING_PASSPHRASE" \
                                        -Dbuild.number="${BUILD_NUMBER}" \
                                        clean deploy
                                    '''

                                    sh '''
                                    mkdir -p mcpb/server
                                    cp target/maverick-ssh-mcp mcpb/server/maverick-ssh-mcp-macos-arm64
                                    '''
                                    stash name: 'maverick-ssh-mcp-native-macos-arm64', includes: 'mcpb/server/maverick-ssh-mcp-macos-arm64'
                                }
                            }
                        }
                    }
                }

                stage('Windows x86_64 Maverick SSH MCP Native') {
                    agent {
                        label 'windows && x86_64'
                    }
                    steps {
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULL_VERSION}"
                        }

                        configFileProvider([
                            configFile(
                                fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                                replaceTokens: true,
                                targetLocation: 'jadaptive.build.properties',
                                variable: 'BUILD_PROPERTIES'
                            )
                        ]) {
                            withMaven(
                                mavenOpts: '--add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED',
                                globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                            ) {
                                
                                withCredentials([string(credentialsId: 'windows-signing-etoken-pin', variable: 'SIGNING_PIN')]) {
                                    bat 'mvn -U -P native-image,windows-signing clean deploy -Dbuild.projectProperties="%BUILD_PROPERTIES%" -Dathene.serverId=athene -Dathene.api=https://athene.jadaptive.com -Dathene.repo=jadaptive -Dathene.windows.sign.key="safenet/b69c9c2e8b5e40d3b5d0d3b97afb2baf" -Dathene.windows.sign.passphrase="%SIGNING_PIN%"'
                                    bat 'if not exist mcpb\\server mkdir mcpb\\server'
                                    bat 'copy /Y target\\maverick-ssh-mcp.exe mcpb\\server\\maverick-ssh-mcp.exe'
                                    stash name: 'maverick-ssh-mcp-native-windows-amd64', includes: 'mcpb/server/maverick-ssh-mcp.exe'
                                }
                            }
                        }

                    }
                }
            }
        }

        stage('MCPB') {
            agent {
                label 'linux && x86_64'
            }
            steps {
                unstash 'maverick-ssh-mcp-native-linux-amd64'
                unstash 'maverick-ssh-mcp-native-linux-arm64'
                unstash 'maverick-ssh-mcp-native-macos-amd64'
                unstash 'maverick-ssh-mcp-native-macos-arm64'
                unstash 'maverick-ssh-mcp-native-windows-amd64'

                script {
                    env.FULL_VERSION = getFullVersion()
                    echo "Full Version : ${env.FULL_VERSION}"

                    def pom = readMavenPom file: 'pom.xml'
                    def manifest = [
                        manifest_version: '0.3',
                        name: pom.artifactId,
                        display_name: pom.name,
                        version: env.FULL_VERSION,
                        description: pom.description,
                        author: [name: 'Jadaptive'],
                        icon: 'icon.png',
                        server: [
                            type: 'binary',
                            entry_point: 'server/maverick-ssh-mcp',
                            mcp_config: [
                                command: '${__dirname}/server/maverick-ssh-mcp',
                                args: ['--mode', 'stdio'],
                                env: [:],
                                platform_overrides: [
                                    win32: [
                                        command: '${__dirname}/server/maverick-ssh-mcp.exe'
                                    ]
                                ]
                            ]
                        ],
                        compatibility: [
                            platforms: ['linux', 'darwin', 'win32']
                        ]
                    ]

                    writeFile(
                        file: 'target/mcpb-bundle/manifest.json',
                        text: groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifest)) + '\n'
                    )
                }

                sh '''
                set -euo pipefail

                mkdir -p target/mcpb-bundle/server

                cp docker/native/maverick-ssh-mcp-linux-amd64 target/mcpb-bundle/server/maverick-ssh-mcp-linux-amd64
                cp docker/native/maverick-ssh-mcp-linux-arm64 target/mcpb-bundle/server/maverick-ssh-mcp-linux-arm64
                cp mcpb/server/maverick-ssh-mcp-macos-amd64 target/mcpb-bundle/server/maverick-ssh-mcp-macos-amd64
                cp mcpb/server/maverick-ssh-mcp-macos-arm64 target/mcpb-bundle/server/maverick-ssh-mcp-macos-arm64
                cp mcpb/server/maverick-ssh-mcp.exe target/mcpb-bundle/server/maverick-ssh-mcp.exe

                cp src/main/mcpb/maverick-ssh-mcp target/mcpb-bundle/server/maverick-ssh-mcp

                chmod +x target/mcpb-bundle/server/maverick-ssh-mcp
                chmod +x target/mcpb-bundle/server/maverick-ssh-mcp-linux-amd64
                chmod +x target/mcpb-bundle/server/maverick-ssh-mcp-linux-arm64
                chmod +x target/mcpb-bundle/server/maverick-ssh-mcp-macos-amd64
                chmod +x target/mcpb-bundle/server/maverick-ssh-mcp-macos-arm64

                cp src/main/icons/icon.png target/mcpb-bundle/icon.png

                npx -y @anthropic-ai/mcpb validate target/mcpb-bundle/manifest.json
                npx -y @anthropic-ai/mcpb pack target/mcpb-bundle target/maverick-ssh-mcp-${FULL_VERSION}.mcpb
                npx -y @anthropic-ai/mcpb info target/maverick-ssh-mcp-${FULL_VERSION}.mcpb
                '''

                withCredentials([usernamePassword(credentialsId: 'athene', usernameVariable: 'ATHENE_USERNAME', passwordVariable: 'ATHENE_PASSWORD')]) {
                    sh '''
                    /usr/local/bin/athene --api=https://athene.jadaptive.com files import jadaptive target/*.mcpb --os=ALL --architecture=ALL --package=maverick-ssh-mcp --version="${FULL_VERSION}" --extension=.mcpb
                    '''
                }

            }
        }

        stage('Docker Hub Image Publish') {
            agent {
                label 'linux && x86_64'
            }
            steps {
                unstash 'maverick-ssh-mcp-native-linux-amd64'
                unstash 'maverick-ssh-mcp-native-linux-arm64'

                script {
                    env.FULL_VERSION = getFullVersion()
                    echo "Full Version : ${env.FULL_VERSION}"
                }

                configFileProvider([
                    configFile(
                        fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',
                        replaceTokens: true,
                        targetLocation: 'jadaptive.build.properties',
                        variable: 'BUILD_PROPERTIES'
                    )
                ]) {
                    sh '''
                    DOCKER_USERNAME=$(grep '^docker.username=' "$BUILD_PROPERTIES" | head -n1 | cut -d= -f2-)
                    DOCKER_PASSWORD=$(grep '^docker.password=' "$BUILD_PROPERTIES" | head -n1 | cut -d= -f2-)

                    if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
                        echo "Missing docker.username or docker.password in $BUILD_PROPERTIES" >&2
                        exit 1
                    fi

                    PUSH_LATEST=false
                    case "${BRANCH_NAME:-main}" in
                        main|master|release/*|release-*) PUSH_LATEST=true ;;
                    esac

                    echo "$DOCKER_PASSWORD" | sudo docker login --username "$DOCKER_USERNAME" --password-stdin

                    sudo docker buildx create --name maverick-ssh-mcp-multiarch --driver docker-container --use || sudo docker buildx use maverick-ssh-mcp-multiarch
                    sudo docker buildx inspect --bootstrap

                    TAG_ARGS="-t jadaptive/maverick-ssh-mcp:${FULL_VERSION}"
                    if [ "$PUSH_LATEST" = true ]; then
                        TAG_ARGS="$TAG_ARGS -t jadaptive/maverick-ssh-mcp:latest"
                    else
                        echo "Skipping latest tag push for branch '${BRANCH_NAME:-unknown}'."
                    fi

                    # shellcheck disable=SC2086
                    sudo docker buildx build \
                        --platform linux/amd64,linux/arm64 \
                        $TAG_ARGS \
                        --push \
                        .

                    sudo docker buildx rm maverick-ssh-mcp-multiarch || true

                    sudo docker logout || true
                    '''
                }
            }
        }
    }
}

String getFullVersion() {
    def pom = readMavenPom file: 'pom.xml'
    def pomVersionArray = pom.version.split('\\.')
    def suffixArray = pomVersionArray[2].split('-')
    return pomVersionArray[0] + '.' + pomVersionArray[1] + '.' + suffixArray[0] + "-${BUILD_NUMBER}"
}


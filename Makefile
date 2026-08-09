# Music Cards — build/test helpers. These are the agent feedback loops.
#
#   make test             fast JVM unit tests (:core, no Android SDK needed)
#   make build            compile everything incl. the Android app
#   make apk              debug APK -> app/build/outputs/apk/debug/app-debug.apk
#   make import           merge Spotify playlist exports (songs/) into data/songs.csv
#   make cards            duplex-printable PDF card sheets from data/songs.csv
#   make cards-html       HTML preview variant of the card sheets
#   make lint             Android lint on :app
#   make validate-ai-docs check that AGENTS.md / docs/ai links are not broken
#   make clean            remove build output

# Gradle needs a JDK 17-21 (AGP 8.5 rejects newer). Respect an exported
# JAVA_HOME if it has javac, otherwise fall back to /opt/java/jdk-21*.
JDK_FALLBACK := $(firstword $(wildcard /opt/java/jdk-21*))
# note: an empty JAVA_HOME must not pass the check — "/bin/javac" exists on
# systems where /bin -> /usr/bin, and the system JDK may be too new for AGP.
USE_FALLBACK :=
ifeq ($(strip $(JAVA_HOME)),)
  USE_FALLBACK := 1
else ifeq ($(wildcard $(JAVA_HOME)/bin/javac),)
  USE_FALLBACK := 1
endif
ifeq ($(USE_FALLBACK),1)
  ifneq ($(strip $(JDK_FALLBACK)),)
    export JAVA_HOME := $(JDK_FALLBACK)
  endif
endif

# Android SDK: local.properties (sdk.dir) wins; otherwise export ANDROID_HOME.
# Without either, Gradle skips the :app module and JVM targets still work.

GRADLE := ./gradlew

.DEFAULT_GOAL := test

.PHONY: test build apk cards cards-html import lint clean check-jdk validate-ai-docs

test: check-jdk
	$(GRADLE) :core:test

build: check-jdk
	$(GRADLE) build

apk: check-jdk
	$(GRADLE) :app:assembleDebug
	@echo ""
	@echo "APK: app/build/outputs/apk/debug/app-debug.apk"

# Playlist exports (Exportify CSV) to merge into data/songs.csv.
IMPORT_SRCS := songs/czsk_vypaovaky.csv \
               songs/sk_-_cz_hity_najlepsie_slovensk_a_esk_pesniky.csv \
               songs/slovensk_hity_70-80-90.csv

import: check-jdk
	$(GRADLE) :cards:importPlaylists --args="data/songs.csv $(IMPORT_SRCS)"

cards: check-jdk
	$(GRADLE) :cards:cardsPdf --args="data/songs.csv build/cards/cards.pdf"
	@echo "Print build/cards/cards.pdf: A4, 100% scale, duplex, FLIP ON LONG EDGE."

cards-html: check-jdk
	$(GRADLE) :cards:run --args="data/songs.csv build/cards"

lint: check-jdk
	$(GRADLE) :app:lintDebug

validate-ai-docs:
	@sh tools/validate_ai_docs.sh

clean: check-jdk
	$(GRADLE) clean

check-jdk:
	@if [ ! -x "$(JAVA_HOME)/bin/javac" ]; then \
		echo "No JDK found (JAVA_HOME=$(JAVA_HOME))."; \
		echo ""; \
		echo "Install one without root:"; \
		echo "  mkdir -p ~/.local/opt && cd ~/.local/opt"; \
		echo "  curl -L -o jdk21.tar.gz \\"; \
		echo "    'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse'"; \
		echo "  tar xzf jdk21.tar.gz && rm jdk21.tar.gz"; \
		exit 1; \
	fi

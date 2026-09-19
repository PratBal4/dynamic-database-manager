#!/bin/bash

# Check if javac is installed
if ! command -v javac &> /dev/null; then
    echo "Java (javac) is not installed."
    read -p "Would you like to install it now? (y/n): " confirm
    if [[ "$confirm" == [yY] || "$confirm" == [yY][eE][sS] ]]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            echo "Installing OpenJDK using Homebrew..."
            brew install openjdk
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
            echo "Installing default-jdk using apt..."
            sudo apt update && sudo apt install -y default-jdk
        else
            echo "Unsupported OS for auto-install. Please install Java manually."
            exit 1
        fi
    else
        echo "Please install Java manually to build the project."
        exit 1
    fi
fi

echo "Compiling the project..."
javac -cp "lib/flatlaf-3.5.4.jar:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar" src/*.java
echo "Compilation completed successfully."

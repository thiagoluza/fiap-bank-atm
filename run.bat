@echo off
chcp 65001 > nul
echo ====================================================
echo        FIAP BANK - EMULADOR DE CAIXA ELETRÔNICO
echo ====================================================
echo.
echo Procurando o Maven do Apache NetBeans...

set MVN_PATH="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MVN_PATH% (
    echo Maven encontrado! Compilando e iniciando a aplicação...
    call %MVN_PATH% clean install -DskipTests
    call %MVN_PATH% -pl presentation exec:java -Dexec.mainClass="com.fiap.bank.atm.AtmApplication"
) else (
    echo.
    echo [AVISO] Maven do NetBeans não encontrado no caminho padrão.
    echo Tentando usar comando 'mvn' global...
    where mvn >nul 2>nul
    if %errorlevel% equ 0 (
        call mvn clean install -DskipTests
        call mvn -pl presentation exec:java -Dexec.mainClass="com.fiap.bank.atm.AtmApplication"
    ) else (
        echo [ERRO] Maven não encontrado. Por favor, abra este projeto
        echo no Apache NetBeans e execute-o diretamente pelo editor,
        echo ou instale o Maven e adicione-o ao seu PATH.
        pause
    )
)

set file=output.txt
set currentdirectory=%cd%

javac *.java ware\*.java bank\*.java marketplace\*.java config\*.java ui\*.java ui\terminal\*.java -d bin 2> "%file%"

rem Check for compilation errors before executing the test suite
call :CheckEmpty "%file%"
exit

:CheckEmpty
if %~z1 gtr 0 exit
cd bin
java commandeconomy.TestSuite 2> "..\%file%"
exit
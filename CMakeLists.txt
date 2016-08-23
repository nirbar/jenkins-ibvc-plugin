CMAKE_MINIMUM_REQUIRED (VERSION 3.4)

PROJECT (IBVC-Jenkins-Plugin)

#######################################################
####  Build with Maven
#######################################################
FIND_PROGRAM( MVN_EXECUTABLE
	NAMES mvn
	PATHS ${CMAKE_SOURCE_DIR}/External/*/ ${CMAKE_SOURCE_DIR}/External/*/bin/ ${CMAKE_SOURCE_DIR}/Externals/*/ ${CMAKE_SOURCE_DIR}/Externals/*/bin/
	DOC "Maven Executable"
	)
IF( NOT (${MVN_EXECUTABLE} STREQUAL MVN_EXECUTABLE-NOTFOUND))
	
	ADD_CUSTOM_TARGET( IBVC-Jenkins-Plugin
		COMMAND ${MVN_EXECUTABLE} install
		WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/
		COMMENT Building Jenkins Plugin
		)
		
	INSTALL( DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/target/*.hpi
		DESTINATION .
		)

ENDIF()
#-----------------------------------------------------------------------
# JOPA
#
# $Id: makefile,v 1.4 2005/02/11 20:22:31 Bob Exp $
#
#-----------------------------------------------------------------------

.SUFFIXES: .java .properties

#-----------------------------------------------------------------------

root = /java/classes
pack = com/nnetworks/jopa
xroot = d:\java\classes
xpack = com\nnetworks\jopa
resp = com/nnetworks/resources
shll = com/nnetworks/shell

T = $(root)/$(pack)
XT = $(xroot)\$(xpack)
R = $(root)/$(resp)
S = $(root)/$(shll)

comp = javac -d $(root) -deprecation -classpath .\;d:\java\classes;d:\oracle\ora92\lib\servlet-api.jar

#-----------------------------------------------------------------------

.java{$(T)}.class :
  $(comp) $<

.properties{$(XT)}.properties :
  @echo copy $< "$@"
  @copy $< "$@" >NUL

#-----------------------------------------------------------------------

objects = $(T)/ConnectionPool.class \
          $(T)/Base64.class \
          $(T)/Processor.class \
          $(T)/Processor_PSP.class \
          $(T)/PathInfo.class \
          $(T)/JOSP.class \
          $(T)/JOSPServer.class \
          $(R)/ResourcePool.class \
          $(XT)/sqlcodes.properties \
          $(T)/JOPAException.class \
          $(T)/JOPA.class \
          $(T)/JOPAShell.class \
          $(T)/JOPAMethod.class

#-----------------------------------------------------------------------

ALL : $(objects)

#-----------------------------------------------------------------------

$(T)/JOPA.class :  $(T)/JOPAException.class $(T)/JOPAShell.class       \
                 $(T)/JOPAMethod.class    $(T)/ConnectionPool.class  \
                 $(T)/PathInfo.class      $(R)/ResourcePool.class

$(T)/Processor.class : $(T)/JOPAException.class $(T)/JOPAShell.class \
                     $(T)/ConnectionPool.class  $(T)/PathInfo.class

$(T)/Processor_PSP.class :  $(T)/Processor.class

$(T)/JOSP.class :  $(T)/JOSPServer.class

#-----------------------------------------------------------------------

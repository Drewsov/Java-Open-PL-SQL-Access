#-----------------------------------------------------------------------
# JOPA.NATIVE
#-----------------------------------------------------------------------

.SUFFIXES: .java .properties

#-----------------------------------------------------------------------

root = /java/classes
base = com/nnetworks/jopa
pack = $(base)/NATIVE
comp = javac -d $(root) -deprecation -classpath .\;d:\java\classes;d:\oracle\ora92\lib\servlet-api.jar
B    = $(root)/$(base)
T    = $(root)/$(pack)

#-----------------------------------------------------------------------

.java{$(T)}.class :
  $(comp) $<

.properties{$(T)}.properties :
  @echo $<
  @copy $< "$@" >NUL


#-----------------------------------------------------------------------

objects = $(T)/NATIVE.class \
          $(T)/GET.class \
          $(T)/POST.class


#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

$(T)/GET.class :   $(T)/NATIVE.class

$(T)/POST.class :  $(T)/NATIVE.class

$(T)/NATIVE.class :  $(B)/Processor_PSP.class

#-----------------------------------------------------------------------

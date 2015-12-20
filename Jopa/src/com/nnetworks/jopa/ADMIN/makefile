#-----------------------------------------------------------------------
# JOPA.ADMIN
#-----------------------------------------------------------------------

.SUFFIXES: .java .properties

#-----------------------------------------------------------------------

root = /java/classes
base = com/nnetworks/jopa
pack = $(base)/ADMIN
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

objects = $(T)/ADMIN.class \
          $(T)/GET.class \
          $(T)/POST.class \
          $(T)/htmlcodes.properties


#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

#$(T)/GET.class :   $(T)/ADMIN.class

#$(T)/POST.class :  $(T)/ADMIN.class

$(T)/ADMIN.class :  $(B)/Processor_PSP.class

#-----------------------------------------------------------------------

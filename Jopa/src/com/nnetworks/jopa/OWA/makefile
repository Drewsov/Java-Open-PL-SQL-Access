#-----------------------------------------------------------------------
# JOPA.OWA
#-----------------------------------------------------------------------

.SUFFIXES: .java .properties

#-----------------------------------------------------------------------

root = /java/classes
base = com/nnetworks/jopa
pack = $(base)/OWA
comp = javac -d $(root) -classpath .\;d:\java\classes;d:\oracle\ora92\lib\servlet-api.jar
B    = $(root)/$(base)
T    = $(root)/$(pack)

resp = com/nnetworks/resources
shll = com/nnetworks/shell
R    = $(root)/$(resp)
S    = $(root)/$(shll)

#-----------------------------------------------------------------------

.java{$(T)}.class :
  $(comp) $<

#-----------------------------------------------------------------------

objects = $(T)/OWA.class \
          $(T)/GET.class \
          $(T)/POST.class


#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

$(T)/GET.class :     $(T)/OWA.class

$(T)/POST.class :    $(T)/OWA.class


$(T)/OWA.class :     $(B)/Processor_PSP.class

#-----------------------------------------------------------------------

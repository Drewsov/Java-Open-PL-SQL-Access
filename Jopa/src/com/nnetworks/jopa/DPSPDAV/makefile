#-----------------------------------------------------------------------
# DPSPDAV
#-----------------------------------------------------------------------

.SUFFIXES: .java

#-----------------------------------------------------------------------

root = \java\classes
comp = javac -d $(root) -deprecation -classpath ./;/java/classes;/oracle/ora92/lib/servlet-api.jar
base = com/nnetworks

B    = $(root)\$(base)\jopa
T    = $(root)\$(base)\jopa/DPSPDAV

#-----------------------------------------------------------------------

.java{$(T)}.class :
  $(comp) $<

#-----------------------------------------------------------------------

objects = $(T)\DPSPDAV.class \
          $(T)\OPTIONS.class \
          $(T)\PROPFIND.class \
          $(T)\MKCOL.class \
          $(T)\DELETE.class \
          $(T)\GET.class \
          $(T)\HEAD.class \
          $(T)\PUT.class \
          $(T)\COPY.class \
          $(T)\MOVE.class \
          $(T)\LOCK.class \
          $(T)\UNLOCK.class

#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

$(T)\DPSPDAV.class :   $(B)\Processor.class \
                     $(T)\DAVNode.class  \
                     $(T)\DAVLock.class \
                     $(T)\IfHeader.class

$(T)\OPTIONS.class :   $(T)\DPSPDAV.class

$(T)\PROPFIND.class :  $(T)\DPSPDAV.class   $(T)\DAVNode.class

$(T)\MKCOL.class :     $(T)\DPSPDAV.class

$(T)\DELETE.class :    $(T)\DPSPDAV.class

$(T)\GET.class :       $(T)\DPSPDAV.class

$(T)\HEAD.class :      $(T)\DPSPDAV.class

$(T)\PUT.class :       $(T)\DPSPDAV.class

$(T)\COPY.class :      $(T)\DPSPDAV.class

$(T)\MOVE.class :      $(T)\DPSPDAV.class

$(T)\LOCK.class :      $(T)\DPSPDAV.class

$(T)\UNLOCK.class :    $(T)\DPSPDAV.class

#-----------------------------------------------------------------------

#-----------------------------------------------------------------------
# JOPA.UDAV
#-----------------------------------------------------------------------

.SUFFIXES: .java

#-----------------------------------------------------------------------

root = /java/classes
base = com/nnetworks/jopa
pack = $(base)/UDAV

comp = javac -source 1.4 -d $(root) -deprecation -classpath .\;d:\java\classes;d:\oracle\ora92\lib\servlet-api.jar

B    = $(root)/$(base)
T    = $(root)/$(pack)

#-----------------------------------------------------------------------

.java{$(T)}.class :
  $(comp) $<

#-----------------------------------------------------------------------

objects = $(T)/UDAV.class \
          $(T)/GET.class \
          $(T)/HEAD.class \
          $(T)/OPTIONS.class \
          $(T)/DELETE.class \
          $(T)/PUT.class \
          $(T)/COPY.class \
          $(T)/MOVE.class \
          $(T)/MKCOL.class \
          $(T)/PROPFIND.class \
          $(T)/LOCK.class \
          $(T)/UNLOCK.class \
          $(T)/IfHeader.class \
          $(T)/LockTable.class \
          $(T)/LockEntry.class

#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

$(T)/UDAV.class :      $(T)/LockEntry.class $(T)/IfHeader.class $(T)/LockTable.class  

$(T)/GET.class :       $(T)/UDAV.class

$(T)/HEAD.class :      $(T)/UDAV.class

$(T)/OPTIONS.class :   $(T)/UDAV.class

$(T)/DELETE.class :    $(T)/UDAV.class

$(T)/PUT.class :       $(T)/UDAV.class

$(T)/COPY.class :      $(T)/UDAV.class

$(T)/MOVE.class :      $(T)/UDAV.class

$(T)/MKCOL.class :     $(T)/UDAV.class

$(T)/PROPFIND.class :  $(T)/UDAV.class

$(T)/LOCK.class :      $(T)/UDAV.class

$(T)/UNLOCK.class :    $(T)/UDAV.class

#-----------------------------------------------------------------------

#-----------------------------------------------------------------------
# PERSES
#-----------------------------------------------------------------------

.SUFFIXES: .java

#-----------------------------------------------------------------------

root = /java/classes
comp = javac -d $(root) -deprecation -classpath .\;d:\java\classes;d:\oracle\ora92\lib\servlet-api.jar
base = com/nnetworks

B    = $(root)/$(base)/jopa
T    = $(root)/$(base)/jopa/PERSES

#-----------------------------------------------------------------------

.java{$T}.class :
  $(comp) $<

#-----------------------------------------------------------------------

objects = $T/PERSES.class \
          $T/OPTIONS.class \
          $T/PROPFIND.class \
          $T/GET.class \
          $T/HEAD.class \
          $T/PUT.class \
          $T/LOCK.class \
          $T/UNLOCK.class \
          $T/DELETE.class \
          $T/MKCOL.class

#          $T/COPY.class \
#          $T/MOVE.class \

#-----------------------------------------------------------------------

OBJ : $(objects)

#-----------------------------------------------------------------------

$T/PERSES.class :    $(B)/Processor.class $T/LockEntry.class $T/Probe.class \
                     $T/LockTable.class

$T/OPTIONS.class :   $T/PERSES.class

$T/PROPFIND.class :  $T/PERSES.class

$T/GET.class :       $T/PERSES.class

$T/HEAD.class :      $T/PERSES.class

$T/PUT.class :       $T/PERSES.class

$T/LOCK.class :      $T/PERSES.class

$T/UNLOCK.class :    $T/PERSES.class

$T/DELETE.class :    $T/PERSES.class

$T/MKCOL.class :     $T/PERSES.class


#$T/COPY.class :      $T/PERSES.class
#$T/MOVE.class :      $T/PERSES.class

#-----------------------------------------------------------------------

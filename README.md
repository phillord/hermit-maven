hermit-maven
============

This is a mavenised version of the
[HermiT](http://hermit-reasoner.com/HermiT) reasoner.

It is built directly from the source repository of the HermiT team and
involves no modifications to their code base. It is not, however, officially
supported by the original authors of HermiT. 


== Building

The top level directory is the original source tree from the HermiT authors.
The HermiT directory contains the maven build. This uses a series of symlinks
to access the HermiT source, test code and resources. I normally build on
linux; it may or may not work on windows. 

The jautomata libraries are included and built along side HermiT. Currently,
the jautomata project appears not to be in active development; this means that
it is unlikely to appear to Maven Central. Forking seems a reasonable way
forward. Again, the code is directly exported from the original source. 

To build, simply move into the HermiT directory and type "mvn install". You
can also build with ant from the directory above. 

== License

The HermiT code is licensed under LGPL. The include Jautomata code is licensed
under a BSD like license. 


== Authors

From the HermiT web page:

HermiT has been developed by Boris Motik, Rob Shearer, Birte Glimm, Giorgos
Stoilos, and Ian Horrocks based on research conducted at the Department of
Computer Science in the University of Oxford in Oxford, England.

This mavenised version has been produced by Phillip Lord
(phillip.lord@newcastle.ac.uk).
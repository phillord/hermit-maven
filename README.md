hermit-maven
============

This is a mavenised version of the
[HermiT](http://hermit-reasoner.com/HermiT) reasoner.

It is built directly from the source repository of the HermiT team and
involves no modifications to their code base. It is not, however, officially
supported by the original authors of HermiT.


Building
--------

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

Using
-----

The dependency and repository information is as follows. 

    <groupId>org.semanticweb.hermit</groupId>
    <artifactId>HermiT</artifactId>
    <version>1.3.7.1</version>

    <repository>
       <id>phillord</id>
        <url>http://homepages.cs.ncl.ac.uk/phillip.lord/maven</url>
    </repository>


Currently, this is not hosted on maven central. It would be nice to do this,
but it is not high priority.

Testing
-------

The test sets for the maven build and the ant build *should* be the same. 
However, some of the tests launch code (not the actual tests) has been
modified; also ant is running JUnit 3, maven is running JUnit 4. So they can
go out of sync. 

The maven build includes the rationals Jautomata tests -- there are 67 in
total, so there should be 67 more tests in the maven build than in the ant. 

License
-------

The HermiT code is licensed under LGPL. The include Jautomata code is licensed
under a BSD like license. 


Authors
-------

From the HermiT web page:

HermiT has been developed by Boris Motik, Rob Shearer, Birte Glimm, Giorgos
Stoilos, and Ian Horrocks based on research conducted at the Department of
Computer Science in the University of Oxford in Oxford, England.

This mavenised version has been produced by Phillip Lord
(phillip.lord@newcastle.ac.uk).


Changes
-------

1.3.7.1 Changes incorporated from Oxford.
1.3.6.2 New tests incorporated from Oxford. Update to 3.4.3 OWL API.

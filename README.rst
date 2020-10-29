====================
iland cloud Java SDK
====================

iland cloud Java SDK for API v1.0

* Free software: BSD License
* iland cloud API doc: https://us-east-1.api.ilandcloud.com/doc/1.0/.
* Java doc: https://api.ilandcloud.com/java/apidocs/1.0.17/.
* iland doc: http://doc.ilandcloud.com

============
Introduction
============

This library provides a Java interface for the `iland cloud API
<https://www.iland.com/>`_.

`iland cloud <http://www.iland.com>`_ provides Enterprise-grade IaaS and this
library is intended to make it even easier for Java programmers to use.

============
Documentation
============

For documentation on using iland's Java SDK check out our `Wiki <https://github.com/ilanddev/java-sdk/wiki>`_. 

============
Installation
============

In your maven configuration start adding a the iland cloud SDK maven artifacts repository::

    <repositories>
        <repository>
            <id>iland-sdk-mvn-repo</id>
            <url>https://raw.githubusercontent.com/ilanddev/java-sdk/mvn-repo</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>always</updatePolicy>
            </snapshots>
    </repository>

You can then add the iland cloud SDK as a dependency::

    <dependency>
        <groupId>com.iland.core</groupId>
        <artifactId>iland-sdk</artifactId>
        <version>1.0.17</version>
    </dependency>


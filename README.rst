===============================
iland cloud Java SDK
===============================

iland cloud Python SDK

* Free software: BSD License
* Java doc: https://api.ilandcloud.com.
* iland cloud API doc: https://api.ilandcloud.com.

============
Introduction
============

This library provides a Java interface for the `iland cloud API
<https://www.iland.com/>`_.

`iland cloud <http://www.iland.com>`_ provides Enterprise-grade IaaS and this
library is intended to make it even easier for Java programmers to use.

============
Installation
============

In your maven configuration start adding a repository from where you will get the iland cloud SDK artifacts::

    <repositories>
        <repository>
            <id>iland-sdk-mvn-repo</id>
            <url>https://raw.githubusercontent.com/ilanddev/java-sdk/mvn-repo</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>always</updatePolicy>
            </snapshots>
    </repository>

You can add the iland cloud SDK as a dependency::

    <dependency>
        <groupId>com.iland.core</groupId>
        <artifactId>iland-sdk</artifactId>
        <version>0.0.2</version>
    </dependency>

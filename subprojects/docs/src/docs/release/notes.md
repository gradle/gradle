The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[SheliakLyr](https://github.com/SheliakLyr),
[James Baiera](https://github.com/jbaiera),
[Patrick Koenig](https://github.com/pkoenig10),
[Matthew Duggan](https://github.com/mduggan),
[David Burström](https://github.com/davidburstrom),
[Nelson Osacky](https://github.com/runningcode),
[Sebastian Schuberth](https://github.com/sschuberth),
[Ismael Juma](https://github.com/ijuma),
[Steve Vermeulen](https://github.com/svermeulen),
and [Lars Kaulen](https://github.com/LarsKaulen).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

## New dependency locking file format

Gradle 6.4 introduces an experimental dependency locking file format.
This format uses a single lock file per project instead of a file per locked configuration.
The main benefit is a reduction in the total number of lock files in a given project.

In addition, when using this format, the lock file name can be configured.
This enables use cases where a given project may resolve different dependency graphs for the same configuration based on some project state.
A typical example in the JVM world are Scala projects where the Scala version is encoded in dependency names.

The format is experimental because it requires opt-in and a migration for existing dependency locking users.
It is however stable and expected to become the default format in Gradle 7.0.

Take a look at [the documentation](userguide/dependency_locking.html#single_lock_file_per_project) for more information and how to enable the feature.

## Improved variant matching error messages

This release introduces new variant matching error messages for the Java ecosystem.
Those error messages could be very intimidating or, sometimes missing context which would let you understand what to do.
Gradle 6.4 takes a first step in improving those error messages by making them more human readable and introducing some coloring on the console to highlight the problems.

<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAoUAAAE5CAMAAADRF6p1AAAC91BMVEUBAQEFAwABAgQGBgUCAwYH
                                AwIEBQaqqqoBAxSbaDCJ4jMNAwICAwtJgaMRBQMpBgKicjkCBBofBwInXpIDBw4YBAJ9RhEgVoyH
                                wx5j2zQFDSKleUBNhKQCF02mo5qK3CUCFBUFCRVEDwKBonyTnqQjEAYlBQICDkECBSkEIFcCBSIY
                                DQYFL2dWdIqA4TOhpaJAeKRLKwiZk18HFzRx3jM2BwKgf0sCFEkEDy2KVB01bp4ILBlglKgIN3CG
                                UBoHJluVazcabCntKSdHEgIyDwJRyDOZo6Z2jJuF4TMJGz8wBgIQQ3uqpXioqaM1WGx6pak5GAGG
                                2C+JziCep6iJ3yxMGQJAIAqlnpKNeVwcNlZZRwIGSCJSiKQ/dpwuZpiFo4MpDwOkqak+CgIkeilk
                                WQWSXSZGaIFpMwqCSxZhi6GLcVGaeU2Kp6pbJASHsRg+cZepnWmmh1EHIxNTHgWokl1lg5d4QAx+
                                2TJgKARpPR58iAx/o4udmmgRJk8vFgodUonpHhGRp6mmkWmXgl92oZIHM2xMwTOHthlEtzNwNwaC
                                ZkLuJhs7GQtaiqNpxDFAOQappIBLnSpzdwuBoaha1zQSXiaNnW6kjlmBYFE9rTNvoqkRTiKpqJEt
                                HwMCCDhajo4LLmN40zKPWiQ5KgQjWo+WYitbBQLCJyhyoR4MPh6FoBOJ1CFZj50LGTKYh27dJyZW
                                NRwYOWSScEZ4XTk3FCJxlaamJSeQo3ZqZUKFlqFbcZQ0oDIGGAM7TQgRDBmcDgKglYYrkTFPfZ2o
                                l3WRoYyH1Cugj3UthCoRMwlKYYk6ZYRWOyYmS3FQGiJdnyttkR1qfpJ1qyEnEx95f4iMeW/qJiDL
                                GhHBnQCHmhFuHyd0UCuBuiJcdQiziQCLm6TMJyhcUAa0ZwCgmABUUjazJSaAICWEqRdAkR+AmRNk
                                lpuLiYJPTTdyCgUTIzlpzjNRqSynEAVZWXZHcRKWRwF9lqhHawCZZgCGpBWGCgJSbliYoIlXXkN9
                                mnAfRE67AABOGUlEQVR42uyaSchOURjHz73n3oteXjNlKvOcKWOmIjIrmcpcZqJEZMq0IEopZIHE
                                DhkzrCTJtFB2FjZWyMLGgo3nuef8vBwuwpvxqe873znP/Dz/e+65X8f8HRRnkfnllKXmP/3L9MtR
                                mEQmtr/Bk/C30dEjNca3ockNa33zc478z5auWafJj1lA7sejHjN6fr9y+BhYQMhK0+Yl891EPPTh
                                36WWbauPQuh3QOG3Uu1Bp76CL1D4s/rwD6AwmTm6x9le0fejsFvvuoXdhv+dhP5vhcIus3aH8X0T
                                CpNBQ38MhZipUbw74udPo3jImw3ll28b/0fhN6Pw9OJfhEJo2JW/DYX1Zy10nRnQfdT0PQ2021LB
                                eqkh+zFre+y4TvcUDYdlYaRBvvOKGkILIwMh32xRu1bL5hn4zE3tnsseTpmwe8iN0bdftRveOB43
                                v4Zaxy5yEPohv8hf/RaHR+vIHD3kgnXeBSbzccDX/G48LhuoUh+Npx+MSn59urebfrExeorCcrOb
                                w2WBY6NDR5Z2Gqz4xC92qUeXWS5+6kIfiAsUxt5f54MLZEF+4R8UFn63E4+pMuEbf1+KqOOkxQ5d
                                s5e377P0RBqisMus4V2HHfgQhdv3Dtg3MEI+3AuQr7lq+ZZmq4aX4DMXFO4/J6tDNl1qUW9M2xHs
                                UNhFLtxrQn6Rv/ottu9tdrNWiTl6yIXr5vitDeUzT8uGOGY7ftywX/tmE6caiHyL9sJk4rlyn6VL
                                IvQEhQMAoVAMCo1xKMQvdqkH8RMPfSBuUFjb+ZO81onk8BL+v4pC4gEVUWJZrBYK8VdMLTeud2jc
                                uV6ev0MdBIWpotBal33LuVPlPF4rFTh7FA6M4pP1LPLWShcAu4yd565Redty40ojD2pfx09kvsbN
                                G/U8lcp8SL/GcwZ2OrbS2kyrjV30FljLQ7TLoRA+dgv81W8xMDW7No3t7/15vZQ8wnVd+/B9LfxU
                                +fHJfu3dukucfL/0Ro631kvRi5tuXzq8XNwF/GKXekj8kcbv40msRyFxexTGWeT9bW1e7vRsscF/
                                ARKAWhCPjL8FCtkLtUuafc06SyJBYSly2UvGC/J6JA4WvLGlq2u0231NLCjRmsQ21Y5JhSKRLw3b
                                VkNIjtGxQ5GfjzCNeg7VgLTqQzsdkxbU1mrLm9T5RS+xDgHo11e/yseO+Pycv/otmkQaNHPiJY9w
                                XdH4IQrhm2YTHz2/2MBQP+pTgEJ9wU6pUaN5Cb24aatHmyYXdwG/2B3SrzxnaBdF4TpX9ACFxKU6
                                xwWFtb0/YY843nuswf+3o5D1jMh+HQqzLrMWikCcyTOZur1wiYnn1JNs+svrsrIX2uRjFHr5unRB
                                vSoyRN6qvGj7xB2KpFgulto9B4JCrboxOQrj1h796Kk7taf2QSF8EBj6c+fcfC/pK/NUDQgqVc++
                                3zPbTs3zFdSnak/WSurNptr1OGvp9ay1ZsC+JfQvkXwjt/d3Or04Ea5ImHyU/ES+/84N5UTOgpnT
                                s/Zk865z+jUIuqB+3NPVSGJWP+yFHoXrm/n4s/wdYYW0cnlcueLLydYO2dTXir+u6s9K4eacShP8
                                a9wVyjRM9Z6TIW7i0bmSf5eRt3IqC8Ywh48dRtbx5+ag8JP8AzmrU/eN3EDOJ/P6LF2YJlK6MZfr
                                lawd8KxJvkcN77r5QC0bBShEXp5S6W9uMFVfXl74F7a0nnm1sYkdv/7sC+11HqJQ9PLu+z04RY+9
                                0KCPX+xKRp/zV3+WnquGl/y8gcSbo7v+LJdHax+H+MtR6M5nL8pZjsLMopdMvN/1zNIlDvDiy53f
                                FpZMnKNQyifOdczxnUT9N24obb7cvKx6cj5L9etkwOmB6cdd4Osks95vY+rIXtjFxV927whNspP0
                                IbPkN2THtGaz65XjlhsXW/UnEL69cWVi+zMHN8b70b7wLBF3JZ4siq2vc8KTrTATdlxBIXP42GGU
                                /J0f7y/W0X6CQnZI5LAnQ+3z8q12LuVbLZFvyRl365WEe/5ajVOR4dsyQKHpo/Lto8zWvzdKvxEz
                                m/vKvHym33QzpgnWW+f8ZEA+T02jj1C4fsDgGkKr+3oUoufcaYi5/VNW9i6bVfjiN7Gf8WfcN+aW
                                1Li5JV6fh7VeX9YdChPNv5fp5OIY6/XcN6/Ww/raDXD5SndP706srAg+89HlX/vBk1Yz7goKRE/l
                                FIWm88bFBShMvd+IuoNC4iceSVL7QD5Zs4nXWs2YbOJGzl/JmI5HDnXIUvyLK1Do/JikgkLiJh6O
                                hTTXAxSsQG4OX2Sxwygs58f5c3JZIQqRw59fTNDyjz7eKjaYsmO7KXLBnNHp4gJUZZY5fOx/ao9n
                                k+iw/+mIhn4zkiU7Pm+eyAT5Iod91pGnHl/tQpKpPPaw87UuVPxUyMUfoCeMn7jwy/In9opR6JJD
                                PqhTgMJq5y9TZOiDz5E/P5sdTM5n4ZwRUX4TB3P42EcvRDVa8AtGVaOL7geUE3qQLz8wWEce51/r
                                AuvfsxdAX0JhGH+4W7CM3DeiUBd0FtYpiL3K+bPIe5w5723HqfQDYoIc8xAZ6sXboQyRPifMPd9i
                                Er2ivRB+8Wiy/BuTuDiv+OPCJ/kiB4P1UD48F4VdIA+qix0I1ARdwE8hCukeciEK8Ut8yNG30A7x
                                os+xMKhTcC6sev5+kTclaGKHLkAhWzn/FA/nfuSNoWPAZw4f+wFf57oAwS8cCZJvNPLATpAvcsTP
                                HHnshd+IYRfyeD/oAnYgnxF+kAvzC/kcgZD7BIX4JT7k6Ftoh3iJx6VgwjoVfCNXNX/IPwt/NGX2
                                Z95K/fPr8WdRfkvzz7+pSfw/6VbqH1+PP4x4o1Xl1uvX7X6zHON33e8jbvKtym1YbsHiJSfzRUIu
                                1/v1tzmD27Xf2a8gf+5EVe/Wa/VvcSLH+AMorG5fuQX73Xq/EVHnn3DrFhT+iluv1Uch/n4rFHIL
                                ttp6xXWuxr1GcMH6d90PJZ5vvvX6H4U/cAv2e/V+YxSCi+qg8M7rXqVq3nrlVmt4exR57HJbEzmI
                                25shCr/hlmmJW6Y/8zZs/JXbsNyCJT/yh48d+NhHD/vEQf2xQ57kjXxo71MUfvl27Tt27iC3iSCI
                                wrATiISUiBuw5wLZcAo2LLgBGyQkViCx4yQcgBMEiT1n4C64nfk0yeu0JrI8pJ3024w8XV1TVXmZ
                                ePCPzaNF1/KF8zlvR3msB6XccOGzX1//fnh7shr1impFaaJAxcuL1kyKFb1ZuXCZMmXCVWjY7Cfv
                                afrTPyXdKr998qM0zF8efepbfJ2PDkPX8oXzOW9HebJOdamn0tXWiO9a1Gu6EOklHi0qvnZhoVpR
                                mjM9Kl5etKY429Gb6cJlyvTnjjKlpFzRpPvSsNlPulB/+m/RrfKnC9Vh/vLoM11Y56N96Fpznvvk
                                C+dz3o7yZJ0LdXFhi3o1haBexVsXX7uwUK01PSpeXrSmONvRm+HCe1OmlJQrmvRQNGy6UH/6J3ly
                                vulCdViXR5/pwjof7UPXmvPcZ94Lc96O8mSdUdfCX+Qt9XrzdwX1epbUa7pQfMuFZTcVelS8lW2i
                                Qmt6Rdu4Hb15Ls4xXShO/kJWFcqU1G2331mv9SEuzue9MPtp3gv1374Xpgu56vMu1vzl0We4MPLd
                                /17IhXkv1KE++cKZnLejPNYXXBhPJ/ksNFGdqNft7tc3qNfaheI3m0J91i58MdGlEwW6ES/vNa05
                                x9k+0Zvn4hzJ9cTJX55OUKZF6pbf+xev9SEuz6Nh/ZSyn3Sh/tKF8lhvuVAd5i+PPvUtvsoX8r4w
                                r8+F+b7QnPXJF87nvB3lyTrVFfW0dHGbvtzsqNey+/QW9ZounJ95J+qV/BQmChU9Kl7ea1pzjqML
                                NKc4x0muJ07+4kKUaZG65fcs57U+xOV5z5bo0+wnXag//beekdOF8qvD/OXRp77Fy7fkwrw+F5qH
                                fOasT75wPuftKE/WeacLu/pX+odQef/Su56/+cHAlXqdxzHMtR/1Pq3T939efvv+sbne6zx6n2tf
                                6n5a27+Ev7+ctdc7nUf3cx0aGhoaGhqq1MN3vi7n3UOFibgc36H61HQH8fUovsP01VP4DtVjU7f0
                                6wF4POvDhZ2rX/p1uPDxqnf61SdF8thXIJBPlzOd6RMr189PkPRhPSlPdQ09hK46p199ai6PfZ5O
                                xKM6Xb/lQutJNqlr6D/o6OhXBJE89nGheDW5fmjZhaWuodV1nPQrmlIe+7hQPKpzXxeqa2hFlf8d
                                fpT0q3uhPHkvFL90L9RHulAGdQ2tqOk7PCud9E6/el8oj31cKB7V6fr5vlAf1oPyRG8OHm4dcWFD
                                J53Tr56R5bGPC8V7Rnb9dKE+rCflOdU1XLiS/EU+zq+l+sfe1YXKFMTxM2fOOVctJ+sj5at8dm1E
                                5LN81CpdDyjhZW0ooUgSIR8lHhQlSp6QeEOElChPUjx4W8ktPPEoH08e/GfO/O7YGQe77uXu+v/q
                                7t4zZ2b+c+b8ds6cPb+dX46CCCxktBbC1vT1zWfhwBG7+GaitSDajYXFSsejHQGjpdCyV2QGg8Fg
                                MBj56tBf71GOYAxGX4JZyOhLzF3Z0UHf3jILGf8Qc8nyr+vu5phZyPgnAAuDqDoq7VGPzro//ca0
                                UQW4ekMd6qhXHfUrnrwtDaaQo6ySQDT3ZKw1v+Nk9AILw6678wXUo0otcOiTZSFGPFe96qpfSYWw
                                VjnHFgctEuGROcOZhYzG5oVjX6bRGKMeVUqqqOqyEOrVSJp8m0ipIpQ7tpTCKrKmkgs1qbA6r+H5
                                B7OQ8Ztj4bBnB1dFUI+qi2k4wmWhp14tQ/2qndswL5xyb3xy4di+S+dmMwsZAFz24K/pOdcntM7z
                                vrg46GpKjv96/9xsLEzHKc/1qs/CzGE/wdqocICFOnW2HFZdslOZs6sArhM+gPh+e+B4z08g2wjG
                                cRTe4Y5zPVgYTL1XNo7/Q8y8MCWV6KSuuz4LiybfyEqmfrVjoXbkLyRi8cR1+yiQsM761lHfAPHh
                                nIrtOsd7dkxsE8A5Xo9GwnOuBwuJQlcLPY7+e6bfuE/3FV3btEoU6lCwMJqn8+EeWRK+V6cSJ0sV
                                Uq8ShRQLHfdn98pb54jvOd4zC9sD3zv0y8B1rs910FfzQjDDr9Jxdw0Tx7WZfpeEAOHPWOg683uO
                                92zc2R5QLABlktjxl8d74jnoh0caYCFSwMLk0r1y8FssxH6Phewc216wTvX0JrDtzMM8B31iYUpv
                                8Pr3WUi58efkCEuVdftBbdcJ350X+s781vGer8jtA+McjzEH23X3qInvoI90/Y9/ic/eUU1dBlBN
                                F/ac8J17ZN+Z3zre8wWZwa7qjH6EiC+LjH4N/mUmg8Fg/HTVSgajITALGf8cIVQGDnpnbdVEuDrD
                                JDZBBetoGECeZqBX1lb1WQiEzML/EFEO2fBVsocm11aNpMtCDWYhoxkWNrO2KrOQ4a9T06MaDaWG
                                mZA52ZAus4T6HFGpMj9W6lVioaJZQmOhFDQWUj5iYZgY1ascRmNhFuc4WIjqrEqBSCpjy8IsLrOw
                                bWFUolArYCxU25Hwx0KoSukfUT+EFivZr5rAwnHZ2qqKqsTCRJIq9rBSvWJtVWnXVs2qC+fRvNCw
                                0FHT6JihZBa2K7RK1NUP4s1lIdQO/s1KEmf3yIdjsFDO0vfIlE+xUML5Pg4ivSYsVYK1VcG5znwW
                                0itfkdsYUkF4LMSVN5+FscfCvQHY7AgTFQtz4gAQNeawMGIWtjWEp6W2vIubY2HisFCtrSrz49h0
                                acrzWPi/QdZdkXHW8Wuj/Hkh7XTnhXuhhq1jYUmvrZrkxAESKernp1Z4yPPC9odWiYIdSMi7IiPd
                                Y6GvhsV+kCknjnOPjPL0pgC9LN8jMxgMBoPRB4ArT7MO882W61q2cU76O/5/bzcFf4KHb6hd4l2t
                                Vg6A3msnjjy/X/vtynzmuKj1/WZu9fd7a+CCq3/JuwlkLv9BO9uPhX3Z/2O2j0rV8+LG9IR+b/W9
                                brFUOfFnvuXdyxvY3xwL0c6GWThwRofG5f7LQhxXX2DM2dO7W4SF17a0Bgupnc2MhcNmXBX9eSxs
                                pv+T2Fe5el6hWjvz/GpMLISqFVj4aNnjDxOXDNduhreHW1VrpnKl3jpPC9gcFlgDNtte6q39Sksg
                                qqcvm1APVLQoh3SURxyochP9JHGSLA5S6Xa+Fb76cqD2heIFn7+cqdX2Bg9edde+TFbkqdUoZCA/
                                v832PzlQ696hkglqxgckB94U6PXVRz0T7Nn/xOQCC6ne7q09YTuvbTHW49fN8VC/PXqZ2k+naSf6
                                E66WTv/kshD5oCrGeUS/o96iyYf4iIPzFs4z5Yfp/t4rRX37C7N+3P5E6jhSzsv6HXFwXDhvOI7m
                                VK7wTbYsfPp1E7HQqloNCw/eGT24a0I5WnEzJWdXgTEKKtcBg46enHfqcoxPdrY9X7gOt6XKXsqx
                                pIB6oKJFOaSjPOKgvWiXFPgsgoXdZflK8ehzbUuqyPhmw4MzH2PcndD2UvmZ9j95eyJ98DrFWOey
                                MDAstPv1lmXhQ6r34Ro7OZov1OsitDscOmfSyBVrvDED7bYsPLpWHR/Wxs1jIfoBqmLsRb+jXuRD
                                fMTBeSuu1+WhWg6l+L32I844E6do4uC4cN5wnhsU/AvXQx4s3F3dSiyEqlWChXOGV+d3XtyhCx+B
                                C7Z1zVY6w/DKYLAQ21jvUKIeNWx0vt8iCULVI8eRijb7FluViyhd1Z+SyjYOryCObS/a5bPwoyDi
                                zCa6adLIF1s0kag+YmGYPHi7T/NRqt06jMdCSv8lCyNJLLRlqCmjxs3Y3HlxH/olvDJnkn/lsu22
                                LNTHZ/sHspOeb/szFqIfoSpGreh31It8iI84OG9jUF6rlsHC3PZHsj5OiDhFE8dloTmOXmPhmsUz
                                nx1cDlWrZWFaXVS6uIMuBKs7OkYVwEKjcsVRF9Aq9IJZ+xUs1MtxXpq5NDb1pEKpaFVrwoFULpLR
                                rSwd5V0Wol0/YKEiyjf2zi40riKK47mfVVKjqUmFVgOtNkajxCixUYwVLBVtkwqhflFLE9RWNNDY
                                UmptLFH7oLRQyIMQYpSibz5YbZFKBDFS8AuEFgRRQR8VH4ofoPjgmbnz29k96TXrktUk7oDeztc5
                                Z87899y5uf89u1pQKHA0/9xtgDSYxcI0kjusKVsEZdZTcaUotDcPTipmY0+ceWDkz66CX1b0nfnh
                                HfpBobebdck18f4xKyePT+zenK6yKGQ8rGKk4nctF/3U2bfXmG/8DQrz7QeF6AnR04gehULWYZt4
                                c6ZYrbBMebPmWa5T2R3ZdgQyTFiCjR+eO9DlsH8x+WncavatfeHphviylQ1p5GOhkdN4eYbC1MY0
                                Ww9MXWJrImOsPresndveiJKrnRznFSkGhWng2hUK0wg7SmOhrJP1ulgIComFDoXB1PdbrH4JlQ0a
                                hbxRNK1GjuCuPkpFWCbfoDA2ON7tWMNyp2c5afer59557NzN7fjFtHcf2Vnk/8xO/Cm5cgPJlVvf
                                ePlOi0JiSBwRG7heUIzCeptzl32UAU3O78htdOPSTH9ETl72rTC/CRRmciZfPfeV2N/s7Dd3fJl/
                                e8HfTSoWlqIwjdhvwQ05fqM6cqJqVivMFtfv2QzydNIAy1WUhvKLEWnPhl1dct7YLqzWpBALQeEz
                                TzeMnBT0hOssqxWWq4ArMV4IQoumiLqcF9aMjPlYKEs5/cxQKtnad0RGTrTKoTA0q4lS2h2qxVaz
                                Ph8LHdvWnQthY4j5nAstCjkX/lYvaLCxMDDnwlceaZZz4d6GqYcaYkHVUCYTdkX2dPLrd61T8q8o
                                MP1WvqDQxKh0/8+JuSPvaJ/a/7PTa9i/H+zbeNWxALvjvk/b5Xzl/A8K48D5s97lyq13/kk4T8WR
                                zT7qXvtHCoUyLmMVN7OPzu/NFzh/gEL0Z3p8LDS5eO18UOj2f2lmf7Q2s5/5cQRu3P7id43COrff
                                7HNgZsKwR4hGoe3PZbnKPw0K227bxTNyXIJCOT8s/ezblgc+FhTCauUZObvD1LscsF1Sj2Kpk/vV
                                G3T1uPkOFXIcizbudvNce2GX0AMK04xtG1oUGvvJiMczMiic+vpL+4wcZ+fCaMo8I2+RXbbPyGaG
                                NHzn+WY8I0/t//K7RwSFdaHtD/fbO/mgebiWugTCr0XOdu+3g7vuWTu2o66J9cizptgX6Tty5s81
                                CblyuSM7/4DCyH0iBP+lKEwzVrHbxzhyfo94RgaFmf41AXpAIfNLUBias/mu9WJ/hN+d/R6FqdUT
                                Rs7vCoUJ+13YZyO4OANb+Sikm0xv5FoFzgxgPHIojIfdqsbFtx4T1+oiTYyD/0idfgr2ap4iuV+5
                                I5+3pE+ZWGiGoEezz2HdIp+LyoHLNO03Jvjx+F/7B7+hj4Jg5HKlsC/oQT5X2pX8sv2HHGWOHs+V
                                onPwyj6buwcoxNuVopD28lCo26Pievjgp+0jLwwhR2eAR49RRb1cFPJ/nk6SHBS+/01rVKpHozDM
                                QSHyy0Uh47X/td/CHBSyfntN81GIfK60K/nl+g85laAQfbLPDbLPWe5g7shBGSgECedFoW3X+8B4
                                jULGF7Nbucqd4IOnkxLryQfLOZX51OnP9SJ1n6M2cCgk4qFH2r/8Zp+0cqc7Xyx0fTNQqHPg4g/l
                                NyaU2KNRWOw39GkUsn4+obkoRD5X2pX8sv2n7WWdejxXjUK/z9bHOXdkWKYahYzTKCTXKuxV5jNe
                                o5Dx6ndLdM7Y0vEB47hjUff92ouaLQvblnmgUOthHHrITUvRbFz8oXPgsg7tN+Zpe7R/8Bv6KNjj
                                /ebUKRSqfeCKfiU/qsB/FPZdjeeqUehzB9dSX9Zy0NZ+T2telNoH0ZY4mL+fR8/dqIS9ONd8Rvnb
                                wMCe1lkGmZflvS9WML/y0nj5UXlJcvXm4WVe/+zjKfavXGWU6vqRO7pex5wU1tlVLRRq9mK1UMjf
                                DcsyGu/lzq8WCp3+eY7Cv/NDyTrmJQpnYY9WgEKi14RwHibH5Zdvz3bWZX/VXDmT8S6crZX1FaAw
                                PNi718+fm4LcSneP+RqFULt6Z+cIxrcuWXJ0i7/mlvL9WPk65gMK39xRGQoJpS0DtwSCwj0Xj/xi
                                LJ30n1XlvUuSSlHI/AWAQtMzCwphcgn6uJZZ8MN8QyFvfGCPwo7ULETYo6Vs1jhS7EXYq4meH959
                                vbwRulesOXpmw+lOmg3onnj85maLwqU3TOybgcKMpepi5J5WLc97b/qLjtOdCd6jv6ffzBte1u3m
                                I08WdNqs9+D9p+4/s+H5i4uOTafkDRR6Q/qd/ILcQ05uvE1i0Z0F/eyenW/tYxMyUt3KBjee+Zk/
                                hjQKi+w/FjTdMLGberkoxB7WiT34kTr9yFfrkG09FkigINCwf/insA6tF3n406Dw+Ohz62kPR+7q
                                ODvGJxAUwh6FHQmbUsdCWJKgUDN2YK/q+Uv75J3ImHAFb5vY8droyiL25ejybZtW56Kw7cj0fa89
                                eUmDvZOINC3Pr37i6ZGxPReDAvrdZzWMAjPfyus08kTcxFftI50Hj35+2/Dxln3+gNB/R7uwNNFL
                                P/KVXM55Wr94ffq+u0+yO0I1GG42S3s0cOOZjz80CtEzOT68zPxHvVwUYg/rxB78SJ1+5Kt1iJyX
                                WyfHQYu3F//oWKj9hD8FhQ+Ovbzer0v0H+zXKIxcDlbYkZptCgph7GgUKrZqPQyk0hPkri7zmZCo
                                30xb+PrE7p6OvdyRBxUK4+v635A3+bJvoFDJ86u3KB7Ce/RrFF7Xf2G9kSfiZAukd9OJrZesG2VK
                                XeNW6ZOCXvqd/N3I1SikH/2H+oVxcKvYwzlh14nx4ZExmaJQiD8UCtGz9OGjd14n8ZB62SjEHrtO
                                bw9+pE4/8tU6ZA8n9vXIWORiL/7RKNR+cv6Ueb3904Nej5kv6NYodLw92JGwTTUKYS/moRB+GyzG
                                wu72nRoQuYPmHITXsz00+i5M3NNJolG4UQ6Nxd7T8orPM7F4DO/Rr1HYM3AM70sA8ijU55cep5d+
                                5CNXoVDrNwcUKb07kLvx6I/fHvjsjyGNQvyhUYiecGPH3kvFXOrlohB7WCf2OD/6uu33/tJ+lHvV
                                HQ9LlYK9+EejUPsJf8pJoKNlr9dj9tXIAoU76wyLMlplGFMu56oUzywn2euhG++RfmJhGsHnaHvz
                                RY/CWNiYlq3qWIyF6T39w9uvPbJpdbdYZSy17Ubf5jfkOyN77jGx0KqJzF8SQnhzkcSuoljo30Qh
                                TxpKYmEavS7eSxPpf9b0RxEozOa7WLglMt6vIBY6vYP5sVD0GxS7+EW55qMzT7x6RnakTBSiRwQ/
                                cUSOcdT/aSwEZdjDp5k6/chX65Agc6Rlg2yNQuGssRB5PhZuOnHb9L0FPYdKYqGwG5fdfZJYWMi5
                                2uzYlMTCNAoEV5596d7Wx1GYthXFwlg4uxlnunGrnZ/wtr2n/+Y1x0c3rW5zKISNIB/1IJQPOygM
                                QoNC3v4Lj7DkXEi7l2fl+/PIPeZLfXc0pInrF46WnK/rQWEaufNMs46FJefCn/y5kH7OOyK33ciV
                                gSL3fOfCHtEv9EA59zT99HuXfwjrWL6tY3gZ45mvUbjuqzUrLpLYgx45UbYMyGmSej4K18r+zzyf
                                gTLsAYXU6Ue+Xod4aMA8lGoU4h/WQdF+wp9m33v672igfXJ8+rA/F8qzrLAbHQrDQs7VOsU2Tcm9
                                CvuSO3IaZXdk2Ivckd18OBRJ4ydXtZz9ZdMgKHTcjCYJ6vb251CY+L8XhhaFGVtyuxhgvOdYqmKn
                                k7fayOfZrP90p2FTPrZB7EBfmojBS0wGRTc/kxfloVDop6d6C8/I2wv9Tn4dcmUfrdy1m+2dbTnP
                                hpl+8a55Fjz7VuJjrMSHgQsTxjNfo3BV36kO+WYleizaBZSFupdn5EwMcbUoLHqqw15Qhj2gkDr9
                                yNfrMHKNdI1C55861qH1Ig9/mn1vvGhiB+3hurtafvgWFObw4ChU0kSzL6VoLov+Lfc8tmYuWxLW
                                K/3eLhlY1M7VyeU84vTMnhMWveUW5M/rcqj/9mBu7SX7xsut1fGTiTwVoJB+U6U7jXJRmMfWzGdL
                                etZrqV1pmqSpb+eqUFhuTlj0LioUhtueW18Ne5te7wXdc+mnuK+zYaOc0X2LZmOWtisU2v64cC4s
                                3k3aQUceWzOfLQnrlX5vVxD7eVw1CsvPCRsHiw6FVbJ33WjL28uqofduuaObtyO+aDambmczPZs1
                                5hm5mKJHO+jIYWvmsiUV6zVAP/FRzfN6/mlOWHutcbr+z6XGpqyVisuiZ1PWyvwok+NlsUArooBo
                                NiX6FO+uTPsqGle5HlifVfBfjh6u57F+vmaGWzgotGUhohCOSZVRiB57raGwukXr03y1qqGQ8i+j
                                kHXNDQoX+W/u11BYQ+F/U2A7hsdPtZx9UrNIDyfhyBcdH0jOTsUuLT3ILC9qh1VJPzlVbf99Ubdj
                                SWp9sWJ/ar2Mx17eNFFvU3J1O2xL9NA/mx7mgQq9zjz/0b52bHpw3ZN71rMuzS7FPuSiJ49lmv3q
                                5foCF8qxj/mt/QVaYDtevfm5w5fx3SzPEm1u3Dp9eEVfZy67NI2ycyHtsCZ9f+m8bseS1Po010Tr
                                ZTz28tacepuSq9thW6KH/tn0MA906HXm+a9o/tsfTgyxrhnsUuxDLno0y9R7KfsFYFBILFzYKITt
                                KLy7wLAyIsUivXbrxDvP5rNLwzTIUEg7rEnfXzqv27Eknb5cFDq9MfYwHnthEFGHfanHoQ+GEXro
                                1+vT85mn2bOaParXQ3td07aB3tsT1jWDXYrdyEWPZpl6LwkB77oDXaBwUdyRC2zHjQM7zbkGFMJG
                                XC3fDxhYIncMzS7VKKQdjoZGIf3djpPh9OWi0OkFhSHjsRc2JXW4Hnoc+sxuez1+3Wp9M/QwT7Nn
                                NXuUedqv5h2YX9dMdil2Ixc9imVaei685t3ViwqFhu1oX7puHHjUxkIywMESTaNoRd/oygbFLh3k
                                fitFUOhZp0LA8rfropyqrh8ULhN9SRY7OD0adESB3S1bT9EbmX43XnhrjybFrFZbD6MAxhjjXHsE
                                q3aFioWe9RkaPfU6FtJPLIT1yTo0e1TFQi+/7UhL/yXNoNC3KxQiFz2aZapjYcZOXiwoNGzHVcJ2
                                XGvPNcTCuMAibex7q/0aYTMqdqmKhbQbFBILDRaIhbAoPQqdvlafFc6gI00y1mQaOb31kevHPvnW
                                TKdhZ8Jq7XZ1UMg42tEH2xIUwvJEjz4X0s88WJ+sQ7NH9bmQ9qWX9u7c1rs8gA1Ku0YhctGTxzKV
                                c+Fhcy6EnWxz1y74c2Hq2I7ZMx4oTBNYpKHpf/6+Qg5U165QCOtUobDVo/BaNw9U8EwJFwf2ZxDe
                                9J5hTWZ6DxcYZ4zHXsemDBxbsx4Usg5YnOiDbYke+p2eGc/I9DMP1ifr1+xR/YxM+6FR+Q7dyaO7
                                YYPSrlGIXPRolmnpM/J22MlZ7tqF/oxs2aMqF6vmCeazUkHhXp0lEtblbDlBKci1TFZfxx7GkxEN
                                BhHtzFPsV+YvbA6NZZku7lLEHo3zUJjDSgWFwuPep1AI67KMnKBKrqFm+Tr2MF6j0LazjsiPK0Eh
                                8hcol8ewTBc7/yNjj6pcrJqt+nc5PK/YvOT+l5pVrtJyc4JSkGujWeDr2MN4jULamafZr349wUJF
                                oWGZLih6bSWFOFiSi1WzVfNYqTk5XsvPqUpRuVWpYw/jQSETaWeeZr8yf2HfkWvlH5QaB7ZWFkuJ
                                axzYWql2yc8rxw02qHD+5Dg/+lbNAqcF/ejV9sxRVlGtZ878Xm4OXl3X/q4VXa648l9CYUV62c35
                                s77ycvBSr6o9cVkl0KUuKLdcNrwsCCZfeDEou8gbqZJ6euvtQX7J8ifukSmSYacK8nVZdflyX2E3
                                NN9vDlCInOqgkBy8CwqF4QwUlg1D+Z28IDh0YLB6KHw0Mdf/BIXBgkbhX+xdTWgkVRDu190z6swy
                                ceNEE1GSiFnFQCA4/jH4E9xAWEHMMP5gVgkYUA9GyMHIKnrRTRDdYMgqS4h/UTBGD55E/AEPMZ5W
                                vImK5KTrZQUvXq33ur6pSU06M469MatdsOlUvXpV9V5/ef16prbeh4/uPxT6nh/61uVOKPTbRuF1
                                H9Jdu4TONXYn9NO6yDVjjav9agzkfNK+4azNjEYJ9A4edd9gGfRvQGFUi9bg5H3wDfbdSftdjSiM
                                ThPU/sATCol/4DD81+6G7/gs7NNf351cZo+J6/agVi7iQ21cfAOns1cju3V33cU9GARmuz6Pj9sL
                                8k0f++PTGBFnRtfgjavhC17ZkXiut/My2FALGHHUqPzq2+Pvr3jF0Y3Ts90Lu6AQP4WksQGFXotI
                                vOTi/HWvXGqydML9R4/fljG2ZmwharEogvzCK9yJ/Jm4tQp6B4/aLM+L8uivUVikk/a/fPKOAp+8
                                fy94ZZ963zj55d33NqKQT9zX/sD3uyzTiwvwL2uCT3zWyGnkVHzqeSqVzITatqiVy/Hdidq4GoXO
                                DuwChY67ceDyu+/1NQqjGrq3GGqfpHaX9WDjhj+Px8VxqlOz42v4gtd2EE9I83I9zYuuBcxxynr6
                                yBeD5Z4PCsXR6oNrpQ/y7aCQ5AqFTfeGUHjr2SN04nMksech25qxRlAIuT2R37x7sUKhIleRjvSo
                                iqeg0NX4ygOFNz3zhDFUi9advE8nrIJvsE8n7bNVhXmL8EZ/4PuveMfyN9Tij0GhrfF47YdPeSDU
                                g7zyGUpNcPF1PnzLtW8+gXqQ0MOJvUEQg0KKW74TEhRyDd0MtU/WMsAobqrNG1h/qKsbg0JVw1dQ
                                CB7tRZof/UT2n6ecJFUL2MWp6MD8ekdxdL2jr2e6qy0UUkOLKCwSLCzO0H7zb0/fdWOXyUYn3BcI
                                hbfVoxByms0IpTEohN7Bo6RHhWLj1kKuRXsnodCdEg6+wX500n5j+ITWnfyB77/C8VfJX9HOKPTe
                                eOHpt16TAsBc2zaPWrm33jjoziGW2ri1qqbGrYXhizui0IvibkAhrtxOKHzKOYU/i9LdUAg9XYMX
                                vMzjLeya10KeF0/VAuY4BIGPnBjL5XoPF0crgkIhBp+3DYW+lfsKhruiEA9oXLndfiDwzkMU3zV0
                                wn3mMqDQqdmxQp53KMGTyLV7zgZ+oD+vTTcQTiwK6C68k4kc3WRRaH86FqdNE+9a61F4hOOkk/bh
                                iK/2/BCLLRfvIPsz1h/zdJZ9xAsKyYPc3SDr1jA/GxQfvuNJK3D3TE7oZ20fKNRroWSvvhjZCWic
                                ZMOO716uw3PzK48xCv1sP6GW9AM+Kz9P+hlqz15+lNdC6kUU8hoWkgWOk6qfOvsB2Q8Cel4Flq2r
                                wavjtrlVrs04O67qb3gT3ZeQ5ilLZ/hzLWB8IRzQ/NbqBi0PTQ8Uj/ce7gcKt50R67u3Er4CheA1
                                DONRCMYTCfRu/ZoGYviEe4vCaLGJUAi5oNDQuKmVfQCKHvrLvjDktfAddnftb5eSFLVoeS18gvhJ
                                ywsKI/tZ+t9GHz3+mASOn3ziPuIif25/RXze8rzfKggKyW9GUGj8213NW89/6+r7n7ZZHqE7URe1
                                bQ9yfEDhhXyyP/aFkr1KdnDCf2AdkB+yH1Lc1z/+GO8LswHpD1h9Pis/z+0B4qbxD5C/TozLeHbe
                                rF2LwtBE9gOf4+rkOPNcm80gbsxrCDsunjDgeeIz/EMDvQPR/NZqqE0M/TK4XMJaqFDoEId/25/L
                                vkah2vU1QyF+uBOvTHTCvaAQtV9ZblHoRSjsd1mbQB8uWe4fvbMe9q7j/tG+kLJDjfn4Z7sViGrR
                                GjyRwQsK2b59533pUCMK+R1Q+YN/QmHEw78hv59e8F5tPMg6tan6rx9CemNg+F0zz7VyM0Ah18at
                                vfOG9dmrNB62eyQb+NZPxrvext3JKLR2H3f6vJ9Ee4B35JD98bttRuKMKkHX7EMP78TIeQIftftB
                                +LGzQ+0uHr5/jEKiSM/LuvkdqNWjzG7SK/IJh8IjgkIhh8BQoZAbWgChRqG+q0CSJ7hUTbLmST+N
                                QktOSPsdsckN2i7kYkPaWaz64SpxovXgUecPnaWjkD6FmrOGD9r3BRS+c4Qz9TltXbr5Uf5ww5n8
                                cqVmnbEmeqiZW1+r9jqKG/asbNdau3KesrNnEK+qxUvNzlkmIXuS8uTHrIXNX5L3AoXoJt6ACpFr
                                uxrFwuhwY1GIITZHoQh1LVr/rU/uRXYt3iX4rqhc85iaupJ9jFq2cSisvwIZFDeyfONRiH6CGolf
                                1e51/ywKrVJS9prvC4magrA5ChlhraDQq28XHvDQKBSgabuQx6IQbHMUGo/9MTUBIWrJ0mxfd/T+
                                HzLIrq2rQet4wZuQrtwcAonZALVs9dn9aEeWcn2tWkYhnFkFE1trF6hhG4gX7YibS5LTz4TsCTnc
                                yTuy/U3ekRUINQrj35E9EQsKRe4pObOiILwIdAQQw6HYFTn+KQNgY1EI/wp14JVA1ZLFSyKya3WW
                                cCwKIfSxUkIf6wlQWJ8trP1ZHZHX19iNq7UrqFG1dLVdAlKi9kBhMwIIm6FQK7Qsh739S95ubclS
                                NvhX7RFwkkr/hL1zmlPjtQvC8w+FewhCPzBJ20sShSiD+q+icOTtse5DJiFKHoXZU733GNPXM9dl
                                uZO5XO7BFvRB9nOsnbS8cwTC4vH1jmY6/UvdnW1AbyQ31bKy9ndIuAM0P+3HBxRCLykU9i3NDZh9
                                TILCiH+kPRS2Tv9FFHYmgMK29E6OVYetZi638f4CZd7kiP7MN+qVSx9kvGRJT14C/SwKIzmh8G/0
                                JxSqc4hO5XItROR/Rd+O3ofrfkBh2yT+9PycSxRiGsfHFo1bjctnK5PUp2cOfRQKv/8/obDliMLP
                                CH3umqKwbb3i6JnjvxQcCsPPqsMNKJx5dXbj2wGatRwRrEnWotyMkyfGv1uAvj+ycYLSGX8Z6Ovp
                                Pj37Y02OLEfH2/7gJ4asfUJQi/bgH/38zVzOPkG4HSiEHxntsYx92PyZZ330Rxx6/sXPoqH5uQ98
                                qyhU8WJcDoVrpR9XavI1Ep+tdCBef/nE+LelHea7/Pts908FL/Y+TIy5JVz0Sz/+Pit/kHFZp9qf
                                r+ZH+4U+4sU4wWu7Wo55YFobmtokd3EoLM5XVtdKZwpYC3XWojzYh44NzvyRh/5IdWF0enlouK+n
                                ulXu6e6EHFmOxeOV1ZnRuS7w+Jtr1R78ox/2MWgHCuGnBpnpAgmXFg3roz/i0CiEn76l6a7+pbku
                                8K2iUMeLcREKF0pzkzX7lGuyOjJU6UC8dFdXHhkTVEDvwHzlns9HFjwtV2GLvFyqThXW0CE261T7
                                89T8aL/QR7wYJ3htV8sxD/wB0CPV4YmhLTyRDwOFAtJjJqR4gEKdtSi/Wx3RH+l9bv77culBisKh
                                G3JkOU4Q739VHQaP0bZqD/41CtEOFMJPbfIrA6PTM6UtPcuIQ6MQfsKveu9Zo/UQfKso1PFiXMXj
                                1Vk725DbcfYvVToQ7wRtk/p6BBXSv/rTSvx9QNgiL5dkULtkncJfHAq1X+gjXowTvLar5W4eQGHx
                                VI7oWMa+ndgVUqNwmazY3kChzloEb63X6wtq5rosDzkye05Sb6IHwWO0rdqDf41CtAOF8FMbT+8X
                                b69/MTusZxlxKBTKOJeHtugPVvgWUajjxbhohzNe3ZJ5tOO0MSDek2NTvJ9S871G1+5VT8tV2CIv
                                q/T6mKxT+ItDofYLfcSLcYLXdrXczQMomOk5lglPdR/CZ0WtroUTnLUYv3a9vONaiCgmeF3RKGzV
                                HvzvthZukRR+5B3r9Jn50+uHW0Uh/JDhM/PdneBbRqGOV9bC9edGK8/V7E3wWoh49dokfv3PR4YA
                                LMg1CiGPQyHagcLma6H2C33Ei3GC13aVXK2FJ8e2PJ/+yGNQSLO1874QWYvgfX7OQx9rF/YLkCOK
                                4vHu1eCbXzvA27+uRdhvwR78Sz+1L6TF6xjNGfzI+9r41Ob4XBf00V+jcO2ngeKp6oMyzvCzcZpI
                                8DEoxP2QVh2vjGu9w0YIed9oZcXuCxGv3U+N1O3ToHdgc6FQLp3JaLlGIeRAoY5LZ51qf3p+tF/o
                                I16ME7y2q+X+tn0hLaLusQcU4vNC/Y6sUYisRbkZIyfGflyVd1rZx50eqntHRhTEv007gAx4Ghe9
                                yhE6WrQH/9yPRpuzNIV3NRrJr7O5Sgf8yBpbHaa7n4E++msUZsm9fSeEH3yuCl7sWTPVYVw9b/uK
                                ouPFuCwKDxDMIcc7MuL1l8/yO6ua77JtX/G0XKMQcqBQx4V2jFv70/Oj/UIf8WKc4LVdLcc8IIkj
                                edL7ov1sL2HCHqCteIsJfD4IFMbHBdpH9zlFYdLkb3ZPthGvP7JQoIdUJoE8GtqdNY/L20/3WWdd
                                7K/ozkcUthcvdh4J+MttLBZaUNxX9zktcppSSimllFJK+4PayyNzWaTnPCcEfnBN6b9KbaAQmVN7
                                gEL4oeu+R2EQUbrTPidVNAlNKtPfa57LTKT6KaKPtUxTYj+47jPSKMQvKQr3BoVeikJLKQoTQqHL
                                ZiSocRYiZzMKBDlL0UTtxgQjNjtRUOiyJVeN4WzKml7U7xAvnAbymbOVe8ql7klkT0KO/pwFKXYV
                                Cu23muarKv1KdVoKOwHD7CWlKExoLbT7QlrvOAuRshlXKJsxBG3LUjw712n4W2jjMTpctuSfBWOz
                                KQfXFqA34/oJCiE39CXuqd5hg7UQcvTnLMgC7GoU9tFes2+pMsko/NcpRWGSKKTMIt+mNrhsxmxY
                                I8rMCTlLMWOoPUsZOfqJnJ1fP0Iocv/5DXrLLqMHKBS5Cb7K5Wg5AwohR3/KO3M1nWBXo9DZtn2a
                                0J4tkSkKE0QhshA5mzEEUZZiuD1L0SJDUBhwtqSxuRvE12VpYl9IuiI3pGhxDBRCjv7IuYDdBhRS
                                wuFn9EDeNyg0KQqT+qTGtxkXqJbE2Yy+v30tdAuYqV8Lt4i32ZIBsikdv2gs0VqYQS6R1YfcFOc3
                                hs50kdSiEHJiFQphF348z13tI3xj1oG7rp6M/KJYEUPgKE7RSbUd9BN1RSkK2yap2WVR6Pl9nIVI
                                2YyDNpsRtVyRpcjtR9y+sEz7QrvBKxjD2ZJYCyl/MNKb4f0kanYVuX+wOTa1mZvKGJs9ma/JO/Ra
                                CLvsx+Mr0chYbtHy2BfuWL8Qcq+hXbTi6xeCRX8Q0KwoReE/pBDZjCFnIXI2o9Ry5SxFtJvAZicu
                                2Jq8Los04GxJoNBAj/vh1kI+MTvd2Te6MWw4exJyjULYJYnzI9eZnuqwoJAoBoVN6xc2RyFUNNxS
                                FCZC528tV0qBImzvSS3XeBSmT+Qk6Tys5UqvQ7Tj3JtarrEoTN9OEqHzt5ZruTT+bafZm1qucfvC
                                9JOaZCit5fpParmmn1onQ2kt179BXvoNXvuU1nJNhrw0m+Ev9s4tNK4ijONnjxsvSUhEN5riQxOx
                                9VIJFlJR462rwXgDG4NC67WtKD5ULGJUvORB2kq01do+aCmoxdZYIT5IfVBTfKj1JV4CDYpC+9B4
                                gaLFF330m93zy0n+8bjbZbc9SeYPus7MN/N9M+ef2TnJ3/kEaWFhxag+C6McTOQtdpn0b8ooaD9x
                                aGQue0/st9YsTM5P7OZ5YVA5xvL5/P4iBX4c3B0EuX0DzbWI09Zr1rCwcijLyMqY2F45lIU11RfW
                                PEt2+El6WHjKkbviDMN7GcqV5lK3bISJT4f2ioE/VrVWWmv8nDQWgiqzkHnMGhZanFe4rHFzgYUx
                                PAtnJwvJuE9GfDL5k9mf7ILFDPktgWb0j7Ik8nQmM/AD2jXzPuORX5hM+9off8SJX+xBqfipZxz8
                                UMZPAMiGKHEzT+K3bIiPf3f3na9gNyOu9onDh8Z3KwvdR774jXxo3/DwMbPbY/9hTZE95bH8eF04
                                CnFjv8RNnLpeOk9dp7SgyEIy7pMRn0z+ZPYnA38xQ/5jGc3ob+2WsfbGBp4OmfPxQrtm3mc8WEim
                                fe2PP+LEL/agVPzUMw5+KOMHXGaZYW+wzLAaN/MkfmPh6x83xPPWuMLRY4tHjg60JJ8L8/tHjg4u
                                M9blj7cs2o095dy+wWX2z4V0xy9xE6eul85T1ilIC4osJOM+2fLJXm0ZZguZ/cnAH2XIr1MWFtov
                                f34ZT4fM+XihnXr8MB4sJNO+9scfceIXe1AqfuoZBz+UlYWWS74wpsbNPInfWPi+a8BO4yrE9qOx
                                KJGFg6vD0cFjxrqBxVPsKds++M/I8HjkO/ZL3MSZtF6yntiefEC6Myyd8AwWwgIy65PJn5zvZOCP
                                MuQ3zGTheTbLTy/k6ZA5Hy+0awZ/xtOM/9off9jhF3tQKn7qGQc/lJWF1qXgSeNmnsRvLCwkvsdO
                                48qO7skXv3uT35Ed0xzrmqbYUw7aD4+7ZqDrRpy6XjrPaeuUIkxnoe4lPMV4LylkyG9I3gstt37A
                                v0HcTj1+GM9yrQeW+b9QZxn/pX8yC7EHpeKnnnHwQzlpL9S4mSHxw0LsNK6R4YFljRPTWXg8YS9s
                                mmoPCxsn8sODqwOAX+JO2gt1ninZC0uwMIgy4pPJn6fIuSrKkN9gG5tl9I9ZWGy/saGQ2d/KUab+
                                FrzQTj1+GM8y/5/jMv9HGf9n9MefshB7UCp+6hkHP5TxE8d945IX7LylcTNP4oeF2GlcxqrmscNF
                                FlIz3jLzXAjrsC+U+T33OMSJ/RI3cep66TxlnYJ0gN8XEq2+Y/IUeceMMuQ3FDP6P5Ih4z7tUSb9
                                DJnzAe3U44fx7B3PZf4n43+g/fFHnPjFHpSKn3rGwQ9l/Og7ssbNPIkfFmKncWVHD+fHjxZZyKlv
                                OD+4+syjeYfjvCPDOuxjFprlP3Fv/BI3cep6yTxT+45cHty5ajZjtsdfoOdtwTzHbH+Ksz3+IPtJ
                                fjxV7xPlwD/FuRV/u31BNwceHh4eHh4eHlXRm1Eurb5MEIHsydvvszw8astCVRwBtEeegx6niIVj
                                hb80Rb9O9fA41SxsCDzmJkR1iTqSv7yg0kSliJqS7qpi5FPVmZSTVJf8BQkW0t4+nDcMNDdO2Ic7
                                F+Ymhg8dXRarLw+5z2H/XT2roapL1JGwEJUmKkXUlEBVjHyietRykuqSv6bDQtp1L3Q6j5E945Pq
                                y7FDxyYGxob/CTxmMVR1iToSFqLSRKWImhKoipFPlD5aTlJdoiyChbQrC9tNaelUIKgvxw7t/nFg
                                5PD+wGMWA9WlqjwpowlBpYh2A6iKkU9Uj1pOUl2isoSFtCsLR/LjmUk1pmfhHIGqLlFHovqEhagU
                                k1hIO5/sfVpW1aXuhagvaU/aCz0L5xJUdYk6EtUnLESlmMRC2vlE9ahlVV3quRD1Je2F3a9Bz4UD
                                LZ6FcwqiukQdieoTFqJSTGIh7XyqOpOyqi71HRn1Je2Od8P2jgwLeUf2LPSoKjgXeuGQx4nBs9Bj
                                DsKz0MPDw8PDw8NjvkIUNjUdj7tPaw1uqCMe/FKu8oqon+qu4623PHxlE+UT00oRj0eZ9/1Vn4UV
                                +eVppmd+jde831CZYo94UsPC8KUnz7EfjJdfqfwWyew115+Qvd5oe4bhqcWsSkX+q8VCxq8CCxmn
                                Fk+dO+teCeYIC4NLnVri8udXnzoWFm8r9iw8YRZ+9OicYeElHz1tUZ7doHeOchcq9XqXqbKAfvzF
                                UO0ZT1W5ykLq1U7HQ+2rLNQ7YPWOWe6MJR78Umb8Sx+8qSDx0PtoND7+wmorea27nYf5M476IW61
                                FxUzcevdtlGcfIdc2URZ1crVvGu29nAMvOy1p+XO0fgnSO8ETdqL6Meqqj3jqSpXWUi92ul4qH2V
                                hXoHrN4xy52xxINfyvGNatdnwpeubNE7WzU+VMjKKsZRP8St9qpiJm69I5bx2Aspq1q5qnfN1h7v
                                PH+b5X6QO0fj2VGvd5kq6Meqqj3jqSq3+DN9QZ3e8ap2Oh5qX4XeAat3zHJPYikWOjWd+57Qewo1
                                PpR32JViocat60U7cesdsUksVLVy9e+aBTW5q/Wq35+53X7k9c5RZke9rq6CftglPQ1V5epeSL3a
                                6XiofRV6B6zeMcudsaVYaG9sz7xj0nW9s1XjQ4VcLgs1bl0v2olb7ohNZKGqlat112zNwebx3v3m
                                n7s/hYXUl8tC1LclWYi6FhbqHa9qp+Oh9lXoHbB6x6zshbFHZWHjAzc+YRW6p2h8uhcyf8ZRPxq3
                                rhftxE2vUnuhqpWre9ds7XHdV24T5u5PZSH1epepgn6ob9We8VSVCwv1jle10/FQ+yr0DljOV9PL
                                nAtjv5TjeG+46N5nku9sJT5UyJzzmD/jqB/iVntVMRO33hGbxEJVK1f3rtna4+K33U+y3jnKXajU
                                612mSSxEfav2jCeq3Ohd745leser2ul4qH0Vegcs75paJh78UmZ8d65366LvmhqfviMzf8ZRP8St
                                9qJiTrzblvFgIWVVK1frrlmPU3MvYTpuuZ/t9yl6VOVphu98elP51umJ2yOFqPBp2vfxvc/WBeUj
                                LXF7eHh4eHh4eMxE41sL7yrHLtz+R09ril/Ji/NoW7CxxZW219fXrzyBeee6e5uDk4jchr0l/bVv
                                q2S9w+X1a4MKgL/yeIH9yWZh27aNKeagsjAI13gWpp+FJxxsV+eHtXoZJJ4q9BMWltt/Jguzb9XX
                                lxFR+GV9/cJ7+EwDCyuA+tP1SRULf5hPLCw7ouxnxr7Cp2dhTJY33l3xwc6p5Pnpz/76la7H+s6/
                                G3LdB77pb90SrOqoN2xsCQ/az/Bd5uvAZmv4ZVGw/o3+af2tfODbRbZqzt68JvnJbt+84vst2E+O
                                17ag9Zv+nybrA/zjR+Mpczz86zwC2mGhzCfX/Vyd+7L5uyGypz9x6PrHfrZmsp/13UO5XBZqvMzL
                                sXCo86edk/VDVv1HbzPxhjs2r/i28z/Wu+vP/tafm5Kf96qewhZOPc8/iSfMW/2Fsj7qF3tZX1rX
                                fLGka8GHmLso+tY2DW2JWdi3cqjzwwY4Xvz+t1Lflu51OzqW5jb07lrfHbMht6l311Dnb03shYl+
                                tnc8t2T9Xw3YM17bgr4jXQtaz6Ee//jReModD/8yj4B2WIifScqsa7LKbVszkT39iUNZiJ+2beta
                                2rcZ2/FbJgs1XuZlLNzSufGhgPr27t5dyzt6m4nX2LtzzZS3QewaN/Xe9fnyLYnPgQlRz/OnVfsx
                                b/UXyPqoX+yVL8D1KOz2sHCdM4lZuLe5bYFVKQsXvrjph67Olas6nsuEX/YtpfuQlbNmAQvVT/zf
                                zia2ZzyLdrHtIUupxz9+NJ5yx8O/spB2WCjzCdf0Lupet77ziK4ycSgL8ZP9cuFdQ7YfUi6XhRov
                                88pt6OvvvTleRzfP9m29zcS7qmdrxvpOYQX9+37emfgcCDuu5/kraGfe6k/XR/1iT7wy8prNPbaX
                                3hyz0Ig+jYW9/8vC7dZ76i85dpg350VZqH7cbKbaM547p7ky9fjHj8ZT7nj4VxbSDgtnzGfhF+/u
                                /aJ/qa4ycQgL43nu6DiyxoalXC4LNV7mZSecFX1H4nV083QxEO/2nrXRuUvWe8g+W3cFWq8spJ7n
                                r6Cdeas/XR/1i72sLxztWLcot+G/Wdj1vyx8tbgXRj/vpfZC9aN7F+PpXoh//Gg85Y6H///bC49Y
                                LX7i5fjmt03f7L25XBbixwb+bZM9JMrlslDjjffCvS92974YUL8q2guJV/em2G/4+fIOiEW9spD6
                                JBbSDgtL74XqF3vi1dF/WbKjU1noVnnhPct7YhY6Nm+tm7kX5ja07jrt61+b43c5ORcm+Amj8w72
                                jMe5iHr840fjKXc8/Mf95Fxom9dzNnGdT/u2FWsPrtjYgj39lYVDPy/KvdW3MsCPkWyFLTjlBBbC
                                u7hV42Ve7u3ERUh9W3fvTncuJF537lo+5ZyGXePBLU1dnb/Vab2ykHqev8ZFO/NWf7o+6hd7Wd8I
                                px+0V5/NM1kY7njX3mmmsrDNXp3cKbjeYS1P2d553rV3uDp9R4aFiX6yyzf3/LQL+5Dx3Dtix5R3
                                ZPzjR+Mpdzz86zx4JzVe/dpf39us87HzzVJ7+nXY019ZeLq5t3dC/BTY7s4llOPx3DB9S/kMguk7
                                isbLvBwLG43m1POOTLzhjj+id1ZZ7y7XvjPQemUh9Tx/jYt25q3+dH3UL/bEG6Qa7lyU5vGqDM4A
                                FcWb41xW7V+8Ete8zRE/71gYHmx9SOItjztbmuzLugp7yul2Ck2Ka75i3rGwsng5eVTBX/2BrT7b
                                pYeHh4eHh0cF2oyKdGSoSGuuCcEPnx6zF9VnIcqp2rMQP/bpWTirUQEbastCfq3lWTiP4FnocXIw
                                VU36X+pFrUdtiZqR9lJqRvrBClVTJqk0qV//R+/NXZ2tD6GepJ7+xMe4ysLtRY3Nyuj+Fo9Ugb+a
                                ozpU9aLWo7bkXEh7KTUj/WCFqimTVJrUO5HBWwuXshdO1tOf+BhXWdhm+2vbtt6HPAvTCBREqA5V
                                vaj1KIxgIe2l1Iz0gxWqpkxSaVIfZO1/EzKRBiyknv7Ex7jKQmdrRE75X8/nK1BTojpU9aLWo7aE
                                hbSXUjPSD1aomjJBpRmPb4ZTdWvU05/4GFdZ6ASHn/X5E2I6wV6o6lH2JK3XvZD2UmpG+qEiVTVl
                                okqT8XObDnSYXBEWUq8sZFz88Glf4Qf6TSjnkUZwLkR1qOpFrQ/lXEh7KTUj/VCRqpoySaVJffZg
                                z9qD9WszqCepVxYyLn74NPb21Btx/bkwjUBNiepQ1Ytaj9oSNSPtpdSM9ENFqmrKJJUm9av67Sej
                                +8Au1JPUKwsZFz/x5/oFdor0LEwlZpviqXKs70z3TSXzGfOGhaev6Zm/muG0Y76wsKtzxbdLAg8P
                                Dw8PDw8Pj7kLl5uIvMRJmfJprwyS3XCq39oi0RPzrBxj+Xx+f+CQ/XFwdxDk9g00VztO1mv+AJaR
                                BbBaLEzDqtYkC3b4iWfhJHJXnGEgVx0oM1d6rVmo/mq9qvg5aSwEVWYh85g1LHSpbB+p8Gkt1iwy
                                +nS0HVTqr/arih99upnqsRDMYCFuK4DEyTxmHQvJqE82vygLIZn7m6LsgWTAJ/N/EFhGfTf3yfaw
                                +HTIjA8SMvQzHtkYM2TS1/74I078Yg9KxE894+CHMn40S2NG42aexG/ZDh//7u47X8FuRlztE4cP
                                je+OyxljYZGM+eI38qF9w8PHzG6P/Yc1RfaUx/LjmXAU4sZ+iZs4db10nrpOaQEsLGTUD6KM9w1R
                                pv4MmfujTKp1YTEDPj9rkz+BZIRljyAzPl5opx4/jAcLi5n075nRH3/EiV/sQan4qWcc/FCescff
                                d+OSG948K6NxM0/iNxa+/rHVYqdxhaPHFo8cHTC6Aj0X5vePHB1cZqzLH29ZtBt7yrl9g7fZP6vp
                                jV/iJk5dL52nrFNQwKn/m1ZoLKxz0V4fWEb9THYyC7YlD7bs1GTut6zShZzpZMDn3AEbrN2qrD16
                                OmTGxw3t1OOH8WChZdI/J5jWX885VxfixC/2oFT81DNf/FDGj2TBzmjczJP4jYWFDP3YSVzw7cJE
                                FhrDwtHBY8a6wlcz9pRtH/xnZHg88h37JW7iTFov4pJ1OlUsPNNeR96fuRfCAhe1A5n6yRNMhv2Q
                                DPgzWHiezfLT1TwdMuPjhXbq8cN4+CeTvvbHH3b4xR6Uip96xsEPZWWhdSl40riZJ/G7/MOuFjuN
                                Kzu6J1/87gX/8Y5sTHOsa5piTzloPzzumoGuG3Hqeuk8ZZ3SshfGLAyz5xf3wmxY2EuyLlN/4SkW
                                95JsaE+PDPhh1maZdcuRZS/MuHZy4tu/w9DqAe3U44fxCpn9zy2wK3SZ9LU//ogTv9iDUvFTzzj4
                                oYwfA2zOuj1G42aexF/Igp3NBthpXCPDA8tOm5jOwuNFmcfkXlgX7YVNmUwGe8pB40R+eHB1ht74
                                JW7i1PXSeeo6FZCC/w/j3/bOHreNGIjCJLEUDEhIlRxAjYv0vsAilQ+RwidR73vEVSDkAsoJ0giQ
                                AFdu5CopXLrMzu5+ofQERi5iZFfmQyx6OENyvHrgj/ViHsyF0fYL068/p/1N/Z65hH1VcwP+xG7A
                                58Z+3RdyUz434+u+kHrGoT9u9ucmfW3PeOTJuMSDU/lTTz+Mg804Ke9uv6V583OSP3exEyd5GQsv
                                1w8dC6nZvjveFzL3EY/t/Lqut1MHGJe8yTP3vMhLnpMbBmbd7wvTuzv7xtm1PUvyLnLG5Ab8/sZ+
                                x436nf8TN+W70N+MD/BTzzj0x83+3KSv7RmPPBmXeHAqf+rph3GwGQeE/uypefNzkj8rMnGaV/Xj
                                od7uYGHHvmV9e3Oxqw1P/RnZwzrisZ2b7+pn7wDjkjd55p4Xeelz+l8Ik4k/rGjN0H6H5UJrh/af
                                OZt9ldn2VVUTT4S058VWhEkDBkp+6hmH/hjfKoK2TxkRx7jEJ2j+/jB/6ukn5UNb6e4ozks+PI/e
                                JE7zct5wWME3WPZCNfHYRser1F6eG3lmn9fx+zGAzeBJFrosC2mhLPT7LMQL8Gsr+qOkjrhTLCQe
                                aP4QUfKn9PR6ioWaNx783Yu2Dsq5KCxkW4YF61I8tn3Wso2a14tZiDUaFlb8EGbLu+hbd8ixkJ5o
                                T0jyU884fX8wlKdK+5MsJB6cyp96+iGfDAuZRzRvMiF/WMi4mhfT3N9YGL35qSYee94s0Nc0SOOS
                                N3nmnhf5y3MazOkkHK1IVRVcV/r9Ra2x+6WSQ2C3ItA+BPNTTz8Af6rvbfozU8cXPqQVKI1LnkDb
                                a/7U04/ajAMyeRFH/jSWOFmRYSE1MdqLwbduluYUjw1PQT9uIG/yzD4vzcfsIZ2RX4jgxo2x5w8d
                                /x2GNBe+lXdx7PmPloWqBUl2HivUl4oQ1nf17ZUbK8bOQh8Hdpx4NRaq4gigPbp2BQWvzULUl6JN
                                f+L3VeVvKhQUFha8ElR1iTqST15QaaJSRE1Jc1UxUqJ6VDunurxAZRk7FuKfL+sGi8vZY1PYvvD9
                                43Kzu07qy42Vy/HuFwucO1Jdoo6Ehag0USmipgSqYqT8iDpT7Jzq8qL/NJ25EL/OhbNG57G62/5R
                                X643vx4X6+WzKxgvoqkuw57qEnUkLESliUoRNSVQtSYlqke1D5QMreoSFt5emcoSFuJXFs6X22gq
                                ENSX682X+8Xq4bsrGC+iqC4j6khsNCGoFNFuAOLwU6J6VDunurxoVJam54WF+JWFq3prut9OBwcL
                                54WFo0Y01WWF6rLTzVWmjmxVn5+7udBUmo1Ksf3ExVhYVRMHaIefEtWj2nt6t5ukukwqS2Ph077f
                                NyyMTTNvtIs2F04n96gvoy9z4TlAVZeoI1F9ohVGpYiaEqiKkRLVo9o51SUqS9SX+NvZb6r7wsWH
                                loVlRT4TBFFdoo5E9cmKjEoxtyLjp0T1qHZOdYnKEvUlfuPdsjkjw8LujHzjyop8TgiVU2WRCXSw
                                0YSgUiQIqIoxldSLneBNzSH/KzsicEt+bCt5beMIs39+pJ8uFbRQ7S+aNmUh0rAcC/H3Jez2aqvq
                                Uv5CRRS2JVvrCwvPCPFYdbmnE04sRKWYYyF+vlA9qi2qS2EhPDvwKwujfVdYeE6IedVlqPZYSH2O
                                hfi1vdp51SUqS9SXyd8U8JJAV1h4ThiOvrAQ6e2isLCgoLCwoKCgoKCgoGAYGM7ppODN4jfM+Aok
                                sRq1uwAAAABJRU5ErkJggg==">

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

## Improvements to code quality plugins

The PMD plugin now supports a new property `maxFailures`. If set, the build will not fail if the number of failures is below the defined treshold.
This can help to introduce PMD into existing projects that may initially have too many warnings.

```
pmd {
    maxFailures = 150
}
```

This was contributed by [Matthew Duggan](https://github.com/mduggan).

As of Gradle 6.4, PMD also enabled [incremental analysis](dsl/org.gradle.api.plugins.quality.Pmd.html#org.gradle.api.plugins.quality.Pmd:incrementalAnalysis) by default.

## Security Improvements

During our investigation of a recent security vulnerability in the [Plugin Portal Publish Plugin](https://blog.gradle.org/plugin-portal-update) we became aware of how much potentially sensitive information is logged when Gradle is executed at `--debug` level.
This information can be publicly exposed when Gradle builds are executed on public CI like Travis CI, CircleCI, & GitHub Actions where build logs are publicly visible.
This information may include sensitive credentials and authentication tokens.
Much of this sensitive information logging occurs deep in components of the JVM and other libraries outside the control of Gradle.
While debugging, this information may be inherently useful for build maintainers.

To strike a balance between the security risks of logging sensitive information and the needs of build maintainers who may find this information useful,
this version of Gradle now warns users about the risks of using `--debug` at the beginning and end of the log on every build.

We recommend plugin maintainers avoid logging sensitive information if possible, and if it's not possible, that all sensitive information be logged exclusively at `--debug` level and no higher.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
